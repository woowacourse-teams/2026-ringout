package com.joon.ringout.presentation.signup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthTerm
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.presentation.signup.model.SignupUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SignupViewModel(
    private val authRepository: AuthRepository,
    private val destinationRepository: DestinationRepository,
    private val productAnalyticsRecorder: ProductAnalyticsRecorder,
    private val currentDate: () -> String = ::currentAgreementDate,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    var uiState by mutableStateOf(SignupUiState())
        private set

    private var nextEventId = 0L
    private var signupSessionId = 0L
    private var pendingSignup: PendingSignup? = null
    private var completedSignupToken: String? = null
    private var signupJob: Job? = null
    private val scope = coroutineScope ?: viewModelScope

    fun startSignup(
        signupToken: String,
        provider: AnalyticsAuthProvider,
    ) {
        signupSessionId++
        signupJob?.cancel()
        signupJob = null
        pendingSignup = PendingSignup(
            signupToken = signupToken,
            provider = provider,
        )
        completedSignupToken = null
        uiState = SignupUiState(hasPendingSignup = true)
    }

    fun signup(agreedTermIds: Set<TermId>) {
        if (uiState.isSaving) return
        val pending = pendingSignup ?: return
        val sessionId = signupSessionId
        val terms = agreedTermIds.mapNotNullTo(mutableSetOf()) { id ->
            when (id) {
                TermId.Service -> AuthTerm.SERVICE
                TermId.Privacy -> AuthTerm.PRIVACY
                else -> null
            }
        }
        uiState = uiState.copy(isSaving = true, errorMessage = null)
        signupJob = scope.launch {
            try {
                if (completedSignupToken != pending.signupToken) {
                    authRepository.signup(
                        signupToken = pending.signupToken,
                        agreedTerms = terms,
                        agreedAt = currentDate(),
                    )
                    if (sessionId != signupSessionId) return@launch
                    completedSignupToken = pending.signupToken
                    runCatching {
                        productAnalyticsRecorder.recordSignupCompleted(pending.provider)
                    }
                }
                if (sessionId != signupSessionId) return@launch
                destinationRepository.sync()
                if (sessionId != signupSessionId) return@launch
                uiState = uiState.copy(
                    isSaving = false,
                    completedEventId = ++nextEventId,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (sessionId != signupSessionId) return@launch
                uiState = uiState.copy(
                    isSaving = false,
                    errorMessage = if (completedSignupToken == pending.signupToken) {
                        error.message ?: "회원가입은 완료됐지만 목적지를 동기화하지 못했어요."
                    } else {
                        error.message ?: "회원가입을 완료하지 못했어요."
                    },
                )
            }
        }
    }

    fun consumeCompletedEvent(eventId: Long) {
        if (uiState.completedEventId == eventId) {
            resetSignup()
        }
    }

    fun resetSignup() {
        signupSessionId++
        signupJob?.cancel()
        signupJob = null
        pendingSignup = null
        completedSignupToken = null
        uiState = SignupUiState()
    }
}

private data class PendingSignup(
    val signupToken: String,
    val provider: AnalyticsAuthProvider,
)

internal expect fun currentAgreementDate(): String
