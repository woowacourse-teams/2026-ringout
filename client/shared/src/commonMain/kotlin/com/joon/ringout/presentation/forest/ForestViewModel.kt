package com.joon.ringout.presentation.forest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.getAuthSession
import com.joon.ringout.domain.forest.ForestRepository
import com.joon.ringout.domain.forest.ForestOnboardingSyncState
import com.joon.ringout.domain.forest.ForestTreeAcknowledgementSyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock

internal data class ForestUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val hasSnapshot: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshErrorMessage: String? = null,
    val onboardingSyncErrorMessage: String? = null,
    val isRetryingOnboardingSync: Boolean = false,
    val treeSyncErrorMessage: String? = null,
    val isRetryingTreeSync: Boolean = false,
    val sessionVersion: Long = 0,
)

internal class ForestViewModel(
    private val repository: ForestRepository,
    private val scope: CoroutineScope? = null,
    private val authSession: AuthSession = getAuthSession(),
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    private val workScope get() = scope ?: viewModelScope
    private val mutableUiState = MutableStateFlow(ForestUiState())
    val uiState = mutableUiState.asStateFlow()
    private var active = false
    private var sessionIdentity = authSession.identity.value
    private var arrivalObserver: Job? = null
    private var refreshJob: Job? = null
    private var onboardingObserver: Job? = null
    private var onboardingRetryJob: Job? = null
    private var treeObserver: Job? = null
    private var treeRetryJob: Job? = null
    private var syncGeneration = 0L
    private var followUpRefresh = false
    private var arrivalObservationFailed = false
    private var requestVersion = 0L

    init {
        workScope.launch {
            combine(authSession.identity, authSession.state) { identity, state -> identity to state }
                .collect { (identity, state) ->
                    if (identity !== sessionIdentity) {
                        cancelWork()
                        sessionIdentity = identity
                        mutableUiState.value = ForestUiState(sessionVersion = uiState.value.sessionVersion + 1)
                    }
                    if (state == AuthSessionState.Authenticated) {
                        if (active) {
                            observeArrivals()
                            observeOnboardingSync()
                            observeTreeSync()
                        }
                    } else {
                        cancelWork()
                        if (state != AuthSessionState.Restoring) {
                            mutableUiState.value = ForestUiState(
                                isLoading = false,
                                errorMessage = "로그인이 필요한 기능이에요.",
                                sessionVersion = uiState.value.sessionVersion,
                            )
                        }
                    }
                }
        }
    }

    /** Only the visible route activates requests; repeated lifecycle starts are idempotent. */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) {
            cancelWork()
            mutableUiState.value = uiState.value.copy(isRefreshing = false, isRetryingOnboardingSync = false, isRetryingTreeSync = false)
        } else if (authSession.state.value == AuthSessionState.Authenticated) {
            observeArrivals()
            observeOnboardingSync()
            observeTreeSync()
        }
    }

    fun retry() {
        if (!active) return
        if (arrivalObserver?.isActive != true) observeArrivals()
        else requestRefresh()
    }

    fun retryOnboardingSync() {
        if (!active || authSession.state.value != AuthSessionState.Authenticated || onboardingRetryJob?.isActive == true) return
        val identity = authSession.identity.value
        val generation = syncGeneration
        onboardingRetryJob = workScope.launch {
            mutableUiState.value = uiState.value.copy(isRetryingOnboardingSync = true)
            try {
                repository.retryOnboardingSync()
                currentCoroutineContext().ensureActive()
                if (canUpdateSync(identity, generation)) observeOnboardingSync()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (canUpdateSync(identity, generation)) showOnboardingSyncFailure()
            } finally {
                if (canUpdateSync(identity, generation)) {
                    mutableUiState.value = uiState.value.copy(isRetryingOnboardingSync = false)
                }
            }
        }
    }

    private fun observeOnboardingSync() {
        if (!active || onboardingObserver?.isActive == true || authSession.state.value != AuthSessionState.Authenticated) return
        val identity = authSession.identity.value
        val generation = syncGeneration
        onboardingObserver = workScope.launch {
            try {
                repository.observeOnboardingSyncState().distinctUntilChanged().collect { state ->
                    currentCoroutineContext().ensureActive()
                    if (canUpdateSync(identity, generation)) {
                        mutableUiState.value = uiState.value.copy(
                            onboardingSyncErrorMessage = if (state == ForestOnboardingSyncState.Blocked) OnboardingSyncFailure else null,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (canUpdateSync(identity, generation)) showOnboardingSyncFailure()
            }
        }
    }

    private fun canUpdateSync(identity: Any?, generation: Long): Boolean =
        active && generation == syncGeneration && identity != null && identity === authSession.identity.value &&
            authSession.state.value == AuthSessionState.Authenticated

    fun retryTreeSync() {
        if (!active || authSession.state.value != AuthSessionState.Authenticated || treeRetryJob?.isActive == true) return
        val identity = authSession.identity.value
        val generation = syncGeneration
        treeRetryJob = workScope.launch {
            mutableUiState.value = uiState.value.copy(isRetryingTreeSync = true)
            try {
                repository.retryTreeAcknowledgementSync()
                currentCoroutineContext().ensureActive()
                if (canUpdateSync(identity, generation)) observeTreeSync()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (canUpdateSync(identity, generation)) showTreeSyncFailure()
            } finally {
                if (canUpdateSync(identity, generation)) {
                    mutableUiState.value = uiState.value.copy(isRetryingTreeSync = false)
                }
            }
        }
    }

    private fun observeTreeSync() {
        if (!active || treeObserver?.isActive == true || authSession.state.value != AuthSessionState.Authenticated) return
        val identity = authSession.identity.value
        val generation = syncGeneration
        treeObserver = workScope.launch {
            try {
                repository.observeTreeAcknowledgementSyncState().distinctUntilChanged().collect { state ->
                    currentCoroutineContext().ensureActive()
                    if (canUpdateSync(identity, generation)) {
                        mutableUiState.value = uiState.value.copy(
                            treeSyncErrorMessage = if (state == ForestTreeAcknowledgementSyncState.Blocked) TreeSyncFailure else null,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (canUpdateSync(identity, generation)) showTreeSyncFailure()
            }
        }
    }

    private fun showTreeSyncFailure() {
        mutableUiState.value = uiState.value.copy(treeSyncErrorMessage = TreeSyncFailure)
    }

    private fun showOnboardingSyncFailure() {
        mutableUiState.value = uiState.value.copy(onboardingSyncErrorMessage = OnboardingSyncFailure)
    }

    fun connect(
        send: (String) -> Unit,
        onReady: () -> Unit,
        onBack: () -> Unit,
        onError: () -> Unit,
        onWebReady: () -> Unit = {},
        onReloadRequired: () -> Unit = onError,
    ): ForestConnection {
        val identity = authSession.identity.value
        fun isCurrentSession() = identity != null && authSession.identity.value === identity &&
            authSession.state.value == AuthSessionState.Authenticated
        return ForestConnection(
            repository, workScope, now, send, onReady, onBack, onError,
            awaitSnapshot = {
                if (!isCurrentSession()) throw CancellationException("숲 로그인 계정이 변경됐습니다.")
                uiState.first { it.hasSnapshot }
                if (!isCurrentSession()) throw CancellationException("숲 로그인 계정이 변경됐습니다.")
            },
            onWebReady = onWebReady,
            canDeliver = ::isCurrentSession,
            onReloadRequired = onReloadRequired,
        )
    }

    private fun observeArrivals() {
        if (!active || arrivalObserver?.isActive == true || authSession.state.value != AuthSessionState.Authenticated) return
        arrivalObservationFailed = false
        arrivalObserver = workScope.launch {
            try {
                // Baseline precedes the first GET, so ARRIVED cannot fall into a subscribe/fetch gap.
                var first = true
                repository.observeSuccessfulArrivalSyncs().distinctUntilChanged().collect {
                    requestRefresh(afterArrival = !first)
                    first = false
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                arrivalObservationFailed = true
                showFailure()
            }
        }
    }

    private fun requestRefresh(afterArrival: Boolean = false) {
        if (!active || authSession.state.value != AuthSessionState.Authenticated) return
        if (refreshJob?.isActive == true) {
            if (afterArrival) followUpRefresh = true
            return
        }
        val version = ++requestVersion
        val identity = authSession.identity.value
        refreshJob = workScope.launch {
            do {
                followUpRefresh = false
                mutableUiState.value = uiState.value.copy(
                    isLoading = !uiState.value.hasSnapshot,
                    isRefreshing = uiState.value.hasSnapshot,
                    errorMessage = null,
                    refreshErrorMessage = null,
                )
                try {
                    withTimeout(20_000) { repository.refreshForest() }
                    currentCoroutineContext().ensureActive()
                    if (version != requestVersion || identity !== authSession.identity.value) return@launch
                    mutableUiState.value = uiState.value.copy(
                        isLoading = false,
                        hasSnapshot = true,
                        isRefreshing = false,
                        errorMessage = null,
                        refreshErrorMessage = if (arrivalObservationFailed) "숲 변경을 확인하지 못했어요." else null,
                    )
                } catch (_: TimeoutCancellationException) {
                    if (version == requestVersion && identity === authSession.identity.value) showFailure()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (version != requestVersion || identity !== authSession.identity.value) return@launch
                    showFailure()
                }
            } while (followUpRefresh && active && identity === authSession.identity.value)
        }
    }

    private fun showFailure() {
        mutableUiState.value = uiState.value.copy(
            isLoading = false,
            isRefreshing = false,
            errorMessage = if (uiState.value.hasSnapshot) null else "숲 기록을 불러오지 못했어요.",
            refreshErrorMessage = if (uiState.value.hasSnapshot) "숲을 새로 불러오지 못했어요." else null,
        )
    }

    private fun cancelWork() {
        requestVersion++
        syncGeneration++
        refreshJob?.cancel()
        refreshJob = null
        arrivalObserver?.cancel()
        arrivalObserver = null
        onboardingObserver?.cancel()
        onboardingObserver = null
        onboardingRetryJob?.cancel()
        onboardingRetryJob = null
        treeObserver?.cancel()
        treeObserver = null
        treeRetryJob?.cancel()
        treeRetryJob = null
        followUpRefresh = false
    }

    private companion object {
        const val OnboardingSyncFailure = "시작 기록을 서버에 저장하지 못했어요."
        const val TreeSyncFailure = "나무 확인 기록을 서버에 저장하지 못했어요."
    }
}
