package com.joon.ringout.presentation.termsreagreement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import com.joon.ringout.domain.terms.RequiredTermType
import com.joon.ringout.domain.terms.RequiredTermsStatus
import com.joon.ringout.domain.terms.TermsRepository
import com.joon.ringout.presentation.signup.currentAgreementDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class TermsReagreementViewModel(
    private val repository: TermsRepository,
    private val authRepository: AuthRepository,
    private val session: AuthSession,
    networkMonitor: NetworkMonitor,
    private val currentDate: () -> String = ::currentAgreementDate,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    var uiState by mutableStateOf(TermsReagreementUiState())
        private set
    private val scope = coroutineScope ?: viewModelScope
    private var identity: Any? = null
    private var network = NetworkStatus.Unknown
    private var requestId = 0L
    private var request: Job? = null
    private var logoutJob: Job? = null

    init {
        scope.launch {
            combine(session.identity, session.state, networkMonitor.status.onStart { emit(NetworkStatus.Unknown) }) {
                key, state, connection -> Triple(key, state, connection)
            }.collect { (key, auth, connection) ->
                val changed = key !== identity || connection != network
                val changedAccount = key !== identity
                identity = key
                network = connection
                if (changedAccount) {
                    cancelRequest()
                    logoutJob?.cancel()
                    uiState = TermsReagreementUiState()
                }
                when {
                    auth == AuthSessionState.Restoring -> uiState = TermsReagreementUiState()
                    auth != AuthSessionState.Authenticated || key == null -> {
                        cancelRequest()
                        uiState = TermsReagreementUiState(phase = TermsGatePhase.Allowed)
                    }
                    connection == NetworkStatus.Offline -> {
                        cancelRequest()
                        // 오프라인 허용은 동의 완료와 별개의 상태다.
                        uiState = uiState.copy(phase = TermsGatePhase.Offline, isSubmitting = false,
                            isChecking = false, errorMessage = null)
                    }
                    connection == NetworkStatus.Unknown -> {
                        cancelRequest()
                        uiState = uiState.copy(phase = TermsGatePhase.Checking, isSubmitting = false, isChecking = false)
                    }
                    changed || uiState.phase == TermsGatePhase.Checking -> checkStatus()
                }
            }
        }
    }

    fun openConsent() {
        if (uiState.phase == TermsGatePhase.Notice) uiState = uiState.copy(phase = TermsGatePhase.Consent)
    }

    fun setAgreed(type: RequiredTermType, agreed: Boolean) {
        if (uiState.phase != TermsGatePhase.Consent || uiState.isSubmitting || uiState.isChecking || uiState.isLoggingOut) return
        if (uiState.status?.pending?.none { it.type == type } != false) return
        uiState = uiState.copy(selected = if (agreed) uiState.selected + type else uiState.selected - type)
    }

    fun setAllAgreed(agreed: Boolean) {
        uiState.status?.pending?.forEach { setAgreed(it.type, agreed) }
    }

    fun retry() {
        if (request?.isActive != true && !uiState.isLoggingOut) checkStatus()
    }

    fun showDetailError() {
        uiState = uiState.copy(errorMessage = "약관 상세를 열지 못했어요. 다시 시도해 주세요.")
    }

    fun submit() {
        if (!uiState.canSubmit || network != NetworkStatus.Online) return
        val shown = uiState.status ?: return
        val key = identity ?: return
        val id = ++requestId
        uiState = uiState.copy(isSubmitting = true, errorMessage = null)
        request = scope.launch {
            var submitted = false
            try {
                // 사용자가 읽은 버전이 제출 전에 바뀌었다면 새 약관을 다시 보여준다.
                val latest = repository.getStatus()
                if (!isCurrent(key, id)) return@launch
                if (!shown.hasSameVersions(latest)) {
                    applyStatus(latest, consent = true, message = "약관이 변경되었어요. 내용을 확인하고 다시 동의해 주세요.")
                    return@launch
                }
                if (!latest.allAgreed) {
                    repository.agreeRequiredTerms(currentDate())
                    submitted = true
                }
                if (!isCurrent(key, id)) return@launch
                val confirmed = if (submitted) repository.getStatus() else latest
                if (!isCurrent(key, id)) return@launch
                applyStatus(confirmed, consent = true,
                    message = if (confirmed.allAgreed) null else "최신 약관의 동의 상태를 다시 확인해 주세요.")
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (!isCurrent(key, id)) return@launch
                uiState = uiState.copy(
                    phase = if (submitted) TermsGatePhase.Failure else TermsGatePhase.Consent,
                    isSubmitting = false,
                    errorMessage = if (submitted) "동의 결과를 확인하지 못했어요. 다시 확인해 주세요."
                        else "약관 동의를 완료하지 못했어요. 다시 시도해 주세요.",
                )
            }
        }
    }

    fun logout() {
        if (uiState.isLoggingOut || uiState.isSubmitting) return
        cancelRequest()
        val key = identity
        uiState = uiState.copy(isLoggingOut = true, isChecking = false, errorMessage = null)
        logoutJob = scope.launch {
            try {
                authRepository.logout()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (key === session.identity.value) uiState = uiState.copy(
                    isLoggingOut = false, errorMessage = "로그아웃하지 못했어요. 다시 시도해 주세요.",
                )
            }
        }
    }

    private fun checkStatus() {
        val key = identity ?: return
        if (network != NetworkStatus.Online || session.state.value != AuthSessionState.Authenticated) return
        cancelRequest()
        val id = requestId
        val isReagreementFlow = uiState.blocksService
        // 최초 조회는 서비스 위에 화면을 띄우지 않는다. 재동의 중 재시도는 현재 화면을 유지한다.
        uiState = uiState.copy(
            phase = if (isReagreementFlow) uiState.phase else TermsGatePhase.Checking,
            isChecking = true,
            isSubmitting = false,
            errorMessage = null,
        )
        request = scope.launch {
            try {
                val status = repository.getStatus()
                if (isCurrent(key, id)) applyStatus(status, consent = isReagreementFlow)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (isCurrent(key, id)) uiState = uiState.copy(
                    phase = if (isReagreementFlow) TermsGatePhase.Failure else TermsGatePhase.CheckFailed,
                    isChecking = false,
                    errorMessage = if (isReagreementFlow) "약관 동의 상태를 확인하지 못했어요. 다시 시도해 주세요." else null,
                )
            }
        }
    }

    private fun applyStatus(status: RequiredTermsStatus, consent: Boolean = false, message: String? = null) {
        uiState = TermsReagreementUiState(
            phase = when {
                status.allAgreed -> TermsGatePhase.Allowed
                consent -> TermsGatePhase.Consent
                else -> TermsGatePhase.Notice
            },
            status = status,
            errorMessage = message,
        )
    }

    private fun isCurrent(key: Any, id: Long): Boolean =
        key === session.identity.value && id == requestId && network == NetworkStatus.Online

    private fun cancelRequest() {
        requestId++
        request?.cancel()
        request = null
    }
}
