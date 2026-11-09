package com.joon.ringout.presentation.appbootstrap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.ThemeMode
import com.joon.ringout.alarm.AlarmLogoutCleanup
import com.joon.ringout.analytics.NoOpOnboardingAnalyticsRecorder
import com.joon.ringout.analytics.OnboardingAnalyticsRecorder
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.firstlaunch.EntryPolicyVersion
import com.joon.ringout.domain.firstlaunch.determineAppEntryDestination
import com.joon.ringout.domain.preferences.AppBootstrapSnapshot
import com.joon.ringout.domain.preferences.AppPreferencesRepository
import com.joon.ringout.domain.preferences.InstallationEntryState
import com.joon.ringout.domain.preferences.SystemThemeModeReader
import com.joon.ringout.data.database.LocalUserDataCleanup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppBootstrapUiState(
    val isReady: Boolean = false,
    val isBootstrapping: Boolean = true,
    val entryErrorMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.Dark,
    val destination: AppEntryDestination? = null,
    val installationEntryState: InstallationEntryState? = null,
    val authSessionState: AuthSessionState = AuthSessionState.Restoring,
    val isAuthFlowInProgress: Boolean = false,
    val hasAuthFlowCompletionFailed: Boolean = false,
    val isOnboardingCompleted: Boolean = false,
    val isAlarmCreationGuidePending: Boolean = false,
    val hasLegacyRuntimeExceptionPending: Boolean = false,
    val isLogoutCleanupPending: Boolean = false,
    val isSaving: Boolean = false,
    val onboardingRetryToken: Int = 0,
    val isSavingAlarmCreationGuide: Boolean = false,
    val hasAlarmCreationGuideSaveFailed: Boolean = false,
    val logoutCleanupErrorMessage: String? = null,
)

enum class AuthFlowEventType {
    SignupRequired,
    LoginAuthenticated,
    SignupCompleted,
}

data class AuthFlowEvent(
    val attemptId: Long,
    val eventId: Long,
    val type: AuthFlowEventType,
)

enum class AuthFlowEventResult {
    Accepted,
    AlreadyHandled,
    Rejected,
}

class AppBootstrapViewModel(
    private val repository: AppPreferencesRepository,
    private val systemThemeModeReader: SystemThemeModeReader,
    private val authRepository: AuthRepository,
    private val authSession: AuthSession,
    private val hasStoredAlarms: suspend () -> Boolean,
    private val alarmLogoutCleanup: AlarmLogoutCleanup,
    private val localUserDataCleanup: LocalUserDataCleanup,
    private val hasRuntimeEvidence: () -> Boolean = { false },
    coroutineScope: CoroutineScope? = null,
    private val onboardingAnalytics: OnboardingAnalyticsRecorder = NoOpOnboardingAnalyticsRecorder,
    private val themeInitializationRetryDelayMillis: Long = ThemeInitializationRetryDelayMillis,
) : ViewModel() {
    var uiState by mutableStateOf(AppBootstrapUiState())
        private set

    private val scope = coroutineScope ?: viewModelScope
    private val themeWriteMutex = Mutex()
    private var latestRequestedThemeMode: ThemeMode? = null
    private var resolvedMissingThemeMode: ThemeMode? = null
    private var isThemeInitializationInProgress = false
    private var isAuthRestoreComplete = false
    private var isBootstrapInitialized = false
    private var latestSnapshot: AppBootstrapSnapshot? = null
    private var bootstrapJob: Job? = null
    private var preferencesJob: Job? = null
    private var authFlowCompletionJob: Job? = null
    private var logoutCleanupJob: Job? = null
    private var nextAuthFlowAttemptId = 0L
    private var activeAuthFlowAttemptId: Long? = null
    private val acceptedAuthFlowEvents = mutableSetOf<AuthFlowEvent>()
    private var acceptedAuthFlowCompletion: AuthFlowEvent? = null

    init {
        scope.launch {
            authSession.state.collect { authState ->
                uiState = uiState.copy(authSessionState = authState)
                publishDestination()
            }
        }
        startBootstrap()
    }

    fun retryInitialization() {
        if (bootstrapJob?.isActive == true) return
        startBootstrap()
    }

    /** Switches entry routing to the auth root before a provider attempt is started. */
    fun requestAuthentication() {
        if (
            uiState.authSessionState == AuthSessionState.Authenticated ||
            uiState.authSessionState == AuthSessionState.Restoring ||
            uiState.isAuthFlowInProgress
        ) return

        activeAuthFlowAttemptId = null
        acceptedAuthFlowEvents.clear()
        acceptedAuthFlowCompletion = null
        authFlowCompletionJob?.cancel()
        uiState = uiState.copy(
            isAuthFlowInProgress = true,
            hasAuthFlowCompletionFailed = false,
            entryErrorMessage = null,
        )
        publishDestination()
    }

    fun beginAuthFlow(): Long {
        val attemptId = ++nextAuthFlowAttemptId
        activeAuthFlowAttemptId = attemptId
        acceptedAuthFlowEvents.clear()
        acceptedAuthFlowCompletion = null
        authFlowCompletionJob?.cancel()
        uiState = uiState.copy(
            isAuthFlowInProgress = true,
            hasAuthFlowCompletionFailed = false,
            entryErrorMessage = null,
        )
        publishDestination()
        return attemptId
    }

    /** Keeps the auth graph mounted until the login/signup completion event reaches the root. */
    fun handleAuthFlowEvent(event: AuthFlowEvent): AuthFlowEventResult {
        if (
            !uiState.isAuthFlowInProgress ||
            event.attemptId != activeAuthFlowAttemptId
        ) {
            return AuthFlowEventResult.Rejected
        }
        if (event in acceptedAuthFlowEvents) return AuthFlowEventResult.AlreadyHandled
        when (event.type) {
            AuthFlowEventType.SignupRequired -> {
                acceptedAuthFlowEvents += event
                return AuthFlowEventResult.Accepted
            }
            AuthFlowEventType.LoginAuthenticated,
            AuthFlowEventType.SignupCompleted -> {
                if (acceptedAuthFlowCompletion != null) return AuthFlowEventResult.Rejected
                acceptedAuthFlowEvents += event
                acceptedAuthFlowCompletion = event
                startAuthFlowCompletion()
                return AuthFlowEventResult.Accepted
            }
        }
    }

    fun cancelAuthFlow(attemptId: Long) {
        if (activeAuthFlowAttemptId != attemptId) return
        authFlowCompletionJob?.cancel()
        activeAuthFlowAttemptId = null
        acceptedAuthFlowEvents.clear()
        acceptedAuthFlowCompletion = null
        uiState = uiState.copy(
            isAuthFlowInProgress = false,
            hasAuthFlowCompletionFailed = false,
            entryErrorMessage = null,
        )
        publishDestination()
    }

    private fun startAuthFlowCompletion() {
        val completion = acceptedAuthFlowCompletion ?: return
        if (authFlowCompletionJob?.isActive == true) return
        authFlowCompletionJob = scope.launch {
            try {
                authSession.state.first { it == AuthSessionState.Authenticated }
                if (activeAuthFlowAttemptId != completion.attemptId) return@launch
                repository.setLegacyRuntimeExceptionPending(false)
                val snapshot = awaitSnapshot {
                    it.installationEntryState?.hasLegacyRuntimeExceptionPending == false
                }
                if (activeAuthFlowAttemptId != completion.attemptId) return@launch
                latestSnapshot = snapshot
                alarmLogoutCleanup.allowScheduling()
                activeAuthFlowAttemptId = null
                acceptedAuthFlowEvents.clear()
                acceptedAuthFlowCompletion = null
                uiState = uiState.copy(
                    isAuthFlowInProgress = false,
                    hasAuthFlowCompletionFailed = false,
                )
                applySnapshot(snapshot)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Keep the auth graph mounted; the current session is still valid and can retry.
                uiState = uiState.copy(
                    hasAuthFlowCompletionFailed = true,
                    entryErrorMessage = "로그인을 마무리하지 못했어요. 다시 시도해 주세요.",
                )
            }
        }
    }

    fun retryAuthFlowCompletion() {
        if (
            uiState.authSessionState == AuthSessionState.Authenticated &&
            acceptedAuthFlowCompletion != null
        ) {
            uiState = uiState.copy(hasAuthFlowCompletionFailed = false, entryErrorMessage = null)
            startAuthFlowCompletion()
        }
    }

    fun requestExplicitLogout() {
        if (uiState.isLogoutCleanupPending || logoutCleanupJob?.isActive == true) return
        if (hasRuntimeEvidence()) {
            uiState = uiState.copy(
                logoutCleanupErrorMessage = "진행 중인 알람이나 미션을 종료한 뒤 로그아웃해 주세요.",
            )
            return
        }
        uiState = uiState.copy(
            isLogoutCleanupPending = true,
            logoutCleanupErrorMessage = null,
        )
        publishDestination()
        runLogoutCleanup()
    }

    fun retryLogoutCleanup() {
        if (!uiState.isLogoutCleanupPending || logoutCleanupJob?.isActive == true) return
        uiState = uiState.copy(logoutCleanupErrorMessage = null)
        runLogoutCleanup()
    }

    fun dismissLogoutCleanupError() {
        uiState = uiState.copy(logoutCleanupErrorMessage = null)
    }

    fun setThemeMode(themeMode: ThemeMode) {
        if (latestRequestedThemeMode == themeMode) return
        latestRequestedThemeMode = themeMode
        scope.launch {
            themeWriteMutex.withLock {
                try {
                    repository.setThemeMode(themeMode)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    if (latestRequestedThemeMode == themeMode) {
                        latestRequestedThemeMode = uiState.themeMode
                    }
                }
            }
        }
    }

    fun completeOnboarding(stepCount: Int = 4) = save(
        block = {
            repository.markOnboardingCompleted()
            applySnapshot(awaitSnapshot {
                it.installationEntryState?.isOnboardingCompleted == true
            })
            runCatching { onboardingAnalytics.recordOnboardingCompleted(stepCount) }
        },
        onFailure = {
            uiState = uiState.copy(onboardingRetryToken = uiState.onboardingRetryToken + 1)
        },
    )

    fun completeAlarmCreationGuide() {
        if (!uiState.isAlarmCreationGuidePending || uiState.isSavingAlarmCreationGuide) return
        uiState = uiState.copy(
            isSavingAlarmCreationGuide = true,
            hasAlarmCreationGuideSaveFailed = false,
        )
        scope.launch {
            try {
                repository.completeAlarmCreationGuide()
                applySnapshot(awaitSnapshot { it.installationEntryState?.isAlarmCreationGuideCompleted == true })
                uiState = uiState.copy(hasAlarmCreationGuideSaveFailed = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                uiState = uiState.copy(hasAlarmCreationGuideSaveFailed = true)
            } finally {
                uiState = uiState.copy(isSavingAlarmCreationGuide = false)
            }
        }
    }

    private fun startBootstrap() {
        bootstrapJob?.cancel()
        preferencesJob?.cancel()
        isBootstrapInitialized = false
        uiState = uiState.copy(
            isReady = false,
            isBootstrapping = true,
            entryErrorMessage = null,
            destination = null,
            installationEntryState = null,
        )
        preferencesJob = scope.launch {
            try {
                repository.bootstrapState.collect { snapshot ->
                    latestSnapshot = snapshot
                    if (isBootstrapInitialized) applySnapshot(snapshot)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                failBootstrap()
            }
        }
        bootstrapJob = scope.launch {
            try {
                var snapshot = repository.bootstrapState.first()
                latestSnapshot = snapshot
                if (!isAuthRestoreComplete) {
                    authRepository.restoreSession()
                    check(authSession.state.value != AuthSessionState.Restoring) {
                        "Authentication restore did not finish."
                    }
                    isAuthRestoreComplete = true
                }
                val authState = authSession.state.value
                check(authState != AuthSessionState.Restoring) {
                    "Authentication restore is still in progress."
                }
                if (snapshot.installationEntryState?.policyVersion != EntryPolicyVersion) {
                    val hasLegacyEvidence = hasRuntimeEvidence() || hasStoredAlarms()
                    repository.initializeEntryPolicy(
                        authSessionState = authState,
                        hasLegacyAlarmOrRuntimeEvidence = hasLegacyEvidence,
                    )
                    snapshot = repository.bootstrapState.first {
                        it.installationEntryState?.policyVersion == EntryPolicyVersion
                    }
                    latestSnapshot = snapshot
                }
                isBootstrapInitialized = true
                applySnapshot(snapshot)
                if (snapshot.installationEntryState?.isLogoutCleanupPending == true) {
                    runLogoutCleanup()
                }
                snapshot.themeMode?.let { mode ->
                    if (latestRequestedThemeMode == null) latestRequestedThemeMode = mode
                } ?: initializeThemeModeIfMissing(resolveMissingThemeMode())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                failBootstrap()
            }
        }
    }

    private fun failBootstrap() {
        isBootstrapInitialized = false
        uiState = uiState.copy(
            isReady = false,
            isBootstrapping = false,
            destination = null,
            entryErrorMessage = "앱을 시작할 수 없어요. 설정을 확인하고 다시 시도해 주세요.",
        )
    }

    private fun applySnapshot(snapshot: AppBootstrapSnapshot) {
        latestSnapshot = snapshot
        val installation = snapshot.installationEntryState
        if (installation?.policyVersion != EntryPolicyVersion) return
        val themeMode = snapshot.themeMode ?: resolveMissingThemeMode()
        if (latestRequestedThemeMode == null) latestRequestedThemeMode = themeMode
        uiState = uiState.copy(
            isReady = true,
            isBootstrapping = false,
            entryErrorMessage = null,
            themeMode = themeMode,
            installationEntryState = installation,
            isOnboardingCompleted = installation.isOnboardingCompleted,
            isAlarmCreationGuidePending = !installation.isAlarmCreationGuideCompleted,
            hasLegacyRuntimeExceptionPending = installation.hasLegacyRuntimeExceptionPending,
            isLogoutCleanupPending =
                uiState.isLogoutCleanupPending || installation.isLogoutCleanupPending,
        )
        publishDestination()
        if (snapshot.themeMode == null) initializeThemeModeIfMissing(themeMode)
    }

    private fun publishDestination() {
        val snapshot = latestSnapshot
        val installation = snapshot?.installationEntryState
        if (!isBootstrapInitialized || installation?.policyVersion != EntryPolicyVersion) {
            uiState = uiState.copy(destination = null)
            return
        }
        uiState = uiState.copy(
            destination = determineAppEntryDestination(
                installation = installation.copy(
                    isLogoutCleanupPending =
                        uiState.isLogoutCleanupPending || installation.isLogoutCleanupPending,
                ),
                authSessionState = authSession.state.value,
                isAuthFlowInProgress = uiState.isAuthFlowInProgress,
            ),
        )
    }

    private fun runLogoutCleanup() {
        if (logoutCleanupJob?.isActive == true) return
        if (hasRuntimeEvidence()) {
            uiState = uiState.copy(
                isLogoutCleanupPending = true,
                logoutCleanupErrorMessage = "진행 중인 알람이나 미션을 종료한 뒤 다시 시도해 주세요.",
            )
            publishDestination()
            return
        }
        logoutCleanupJob = scope.launch {
            try {
                repository.setLogoutCleanupPending(true)
                awaitSnapshot { it.installationEntryState?.isLogoutCleanupPending == true }
                alarmLogoutCleanup.cancelScheduledAlarms()
                localUserDataCleanup.clear()
                authRepository.logout()
                repository.setLegacyRuntimeExceptionPending(false)
                repository.setLogoutCleanupPending(false)
                val snapshot = awaitSnapshot {
                    it.installationEntryState?.isLogoutCleanupPending == false
                }
                uiState = uiState.copy(
                    isLogoutCleanupPending = false,
                    logoutCleanupErrorMessage = null,
                )
                applySnapshot(snapshot)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                uiState = uiState.copy(
                    isLogoutCleanupPending = true,
                    logoutCleanupErrorMessage = "로그아웃 데이터를 정리하지 못했어요. 다시 시도해 주세요.",
                )
                publishDestination()
            }
        }
    }

    private suspend fun awaitSnapshot(predicate: (AppBootstrapSnapshot) -> Boolean): AppBootstrapSnapshot =
        repository.bootstrapState.first(predicate)

    private fun resolveMissingThemeMode(): ThemeMode =
        resolvedMissingThemeMode ?: systemThemeModeReader.read().also {
            resolvedMissingThemeMode = it
        }

    private fun initializeThemeModeIfMissing(themeMode: ThemeMode) {
        if (isThemeInitializationInProgress) return
        isThemeInitializationInProgress = true
        scope.launch {
            try {
                while (true) {
                    try {
                        themeWriteMutex.withLock {
                            repository.initializeThemeModeIfMissing(themeMode)
                        }
                        return@launch
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        delay(themeInitializationRetryDelayMillis)
                    }
                }
            } finally {
                isThemeInitializationInProgress = false
            }
        }
    }

    private fun save(
        block: suspend () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        if (uiState.isSaving) return
        uiState = uiState.copy(isSaving = true)
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                onFailure()
            } finally {
                uiState = uiState.copy(isSaving = false)
            }
        }
    }
}

private const val ThemeInitializationRetryDelayMillis = 1_000L
