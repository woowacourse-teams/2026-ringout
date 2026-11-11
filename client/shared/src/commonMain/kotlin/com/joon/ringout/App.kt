package com.joon.ringout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.createSavedStateHandle
import com.joon.ringout.alarm.ForceEndMissionResult
import com.joon.ringout.alarm.rememberAlarmLogoutCleanup
import com.joon.ringout.presentation.activemission.ForceEndMissionViewModel
import com.joon.ringout.presentation.activemission.ForceEndPhase
import com.joon.ringout.presentation.activemission.ActiveAlarmTrackingScreen
import com.joon.ringout.presentation.activemission.component.ForceEndMissionStatus
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.DefaultMissionLocationState
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.di.AppContainer
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.firstlaunch.shouldShowLimitedRuntimeControls
import com.joon.ringout.domain.push.RoomActivityPushRequest
import com.joon.ringout.presentation.alarmguide.AlarmCreationGuideRoute
import com.joon.ringout.presentation.alarmguide.AlarmCreationGuideSurface
import com.joon.ringout.presentation.alarmguide.AlarmCreationGuideViewModel
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.app.AuthSessionCoordinator
import com.joon.ringout.presentation.app.AppLocationCacheViewModel
import com.joon.ringout.presentation.app.AppRuntimeCoordinator
import com.joon.ringout.presentation.arrival.PendingArrivalUiState
import com.joon.ringout.presentation.arrival.PendingArrivalViewModel
import com.joon.ringout.presentation.arrival.RestrictedArrivalHost
import com.joon.ringout.presentation.arrival.shouldEnableRestrictedArrival
import com.joon.ringout.presentation.app.HomeNotificationPermissionEffect
import com.joon.ringout.presentation.app.rememberAppAlarmController
import com.joon.ringout.presentation.appbootstrap.AppBootstrapViewModel
import com.joon.ringout.presentation.appbootstrap.AuthFlowEvent
import com.joon.ringout.presentation.appbootstrap.AuthFlowEventResult
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.destination.DestinationCurrentLocationCache
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.login.LoginViewModel
import com.joon.ringout.presentation.signup.SignupViewModel
import com.joon.ringout.presentation.navigation.authGraph
import com.joon.ringout.presentation.navigation.rememberAuthNavigation
import com.joon.ringout.presentation.navigation.rememberAuthOnlyNavigationState
import com.joon.ringout.presentation.mypage.MyPageViewModel
import com.joon.ringout.presentation.mypage.model.MyPageAccountAction
import com.joon.ringout.presentation.roomcreate.RoomCreateViewModel
import com.joon.ringout.presentation.roomedit.RoomEditViewModel
import com.joon.ringout.presentation.roomhome.RoomHomeViewModel
import com.joon.ringout.presentation.roomactivity.RoomActivityViewModel
import com.joon.ringout.presentation.roomhome.blocksNavigationBack
import com.joon.ringout.presentation.roomlist.RoomListViewModel
import com.joon.ringout.presentation.roomlist.model.RoomMutationType
import com.joon.ringout.presentation.onboarding.OnboardingRoute
import com.joon.ringout.presentation.ringing.AlarmRingingUiState
import com.joon.ringout.presentation.ringing.AlarmRingingRoute
import com.joon.ringout.presentation.navigation.AppRoute
import com.joon.ringout.presentation.navigation.RingoutNavHost
import com.joon.ringout.presentation.navigation.alarmRuntimeGraph
import com.joon.ringout.presentation.navigation.arrivalGraph
import com.joon.ringout.presentation.navigation.alarmEditorGraph
import com.joon.ringout.presentation.navigation.rememberAlarmEditorNavigation
import com.joon.ringout.presentation.navigation.homeGraph
import com.joon.ringout.presentation.navigation.rememberAppNavigationState
import com.joon.ringout.presentation.navigation.rememberNavigationViewModelScopes
import com.joon.ringout.presentation.currentLocalClockSnapshot
import com.joon.ringout.presentation.termsreagreement.TermsReagreementViewModel
import com.joon.ringout.presentation.termsreagreement.TermsReagreementRoute
import com.joon.ringout.presentation.termsreagreement.TermsGatePhase
import com.joon.ringout.presentation.to24HourTimeString

@Composable
fun App(
    appContainer: AppContainer,
    appVersion: String = "",
    myPageExtraContent: (@Composable (ThemeMode) -> Unit)? = null,
    useSystemLocationPermissionUiOnly: Boolean = false,
    ringingAlarm: AlarmRingingUiState? = null,
    hasExternalRingingAlarm: Boolean = false,
    activeAlarmMission: ActiveAlarmMission? = null,
    activeAlarmMissionLocation: ActiveAlarmMissionLocation? = null,
    missionLocationState: MissionLocationState = DefaultMissionLocationState,
    onRequestWhenInUseLocation: () -> Unit = {},
    onRequestAlwaysLocation: () -> Unit = {},
    onConfirmAlwaysLocationResult: () -> Unit = {},
    onRequestTemporaryFullAccuracy: () -> Unit = {},
    onRingingAlarmDismiss: (String) -> Unit = {},
    onActiveAlarmMissionExpired: () -> Unit = {},
    onActiveAlarmMissionForceEnd: suspend (occurrenceId: String) -> ForceEndMissionResult = { ForceEndMissionResult.Failed },
    onActiveAlarmMissionForceEndHoldStarted: (occurrenceId: String) -> Unit = { _ -> },
    onActiveAlarmMissionForceEndHoldCancelled:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit = { _, _ -> },
    onActiveAlarmMissionForceEndHoldCompleted:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit = { _, _ -> },
    pendingRoomActivityPush: RoomActivityPushRequest? = null,
    onRoomActivityPushHandled: (requestId: String) -> Unit = {},
) {
    LifecycleResumeEffect(appContainer) {
        appContainer.onAppResumed()
        onPauseOrDispose { }
    }
    val alarmLogoutCleanup = rememberAlarmLogoutCleanup()
    val currentRuntimeEvidence = rememberUpdatedState(ringingAlarm != null || activeAlarmMission != null)
    val appBootstrapViewModel = viewModel {
        AppBootstrapViewModel(
            repository = appContainer.appPreferencesRepository,
            onboardingAnalytics = appContainer.productAnalyticsRecorder,
            systemThemeModeReader = appContainer.systemThemeModeReader,
            authRepository = appContainer.authRepository,
            authSession = appContainer.authSession,
            hasStoredAlarms = appContainer.hasStoredAlarmsForEntryMigration,
            hasRuntimeEvidence = { currentRuntimeEvidence.value },
            alarmLogoutCleanup = alarmLogoutCleanup,
            localUserDataCleanup = appContainer.localUserDataCleanup,
            currentVerifiedAlarmAccountId = appContainer.currentVerifiedAlarmAccountId,
            migrateLegacyAlarmData = appContainer.migrateLegacyAlarmData,
        )
    }
    val appBootstrapUiState = appBootstrapViewModel.uiState
    val authSessionState = appBootstrapUiState.authSessionState
    val pendingArrivalViewModel = viewModel { PendingArrivalViewModel(appContainer.arrivalResultRepository) }
    val pendingArrival by pendingArrivalViewModel.uiState.collectAsStateWithLifecycle()
    val authSessionIdentity by appContainer.authSession.identity.collectAsStateWithLifecycle()
    val latestFcmToken by appContainer.pushTokenSource.latestFcmToken.collectAsStateWithLifecycle()
    LaunchedEffect(authSessionState, latestFcmToken, appContainer.pushTokenRegistrationReadiness) {
        appContainer.pushTokenRegistrationReadiness.updateAuthSession(authSessionState)
        appContainer.pushTokenRegistrationReadiness.updateFcmToken(latestFcmToken)
    }
    val termsViewModel: TermsReagreementViewModel = viewModel {
        TermsReagreementViewModel(
            repository = appContainer.termsRepository,
            authRepository = appContainer.authRepository,
            session = appContainer.authSession,
            networkMonitor = appContainer.networkMonitor,
            explicitLogout = appBootstrapViewModel::requestExplicitLogout,
            canStartExplicitLogout = appBootstrapViewModel::canOpenExplicitLogoutConfirmation,
        )
    }

    val destinationCurrentLocationCache = if (
        appBootstrapUiState.destination == AppEntryDestination.Home ||
        appBootstrapUiState.destination == AppEntryDestination.Guide
    ) {
        viewModel { AppLocationCacheViewModel(appContainer.authSession) }
            .destinationCurrentLocationCache
    } else {
        null
    }

    RingoutTheme(themeMode = appBootstrapUiState.themeMode) {
        RestrictedArrivalHost(
            repository = appContainer.arrivalResultRepository,
            missionHistoryRepository = appContainer.missionHistoryRepository,
            hasActiveRuntime = hasExternalRingingAlarm || ringingAlarm != null || activeAlarmMission != null,
            isEnabled = shouldEnableRestrictedArrival(
                isAppReady = appBootstrapUiState.isReady,
                entryDestination = appBootstrapUiState.destination,
                areGuideTermsAllowed = termsViewModel.uiState.phase == TermsGatePhase.Allowed,
            ),
        ) {
            when {
                !appBootstrapUiState.isReady -> AppBootstrapSurface(
                    isLoading = appBootstrapUiState.isBootstrapping,
                    errorMessage = appBootstrapUiState.entryErrorMessage,
                    onRetry = appBootstrapViewModel::retryInitialization,
                )

                appBootstrapUiState.destination == AppEntryDestination.LogoutCleaning &&
                    (ringingAlarm != null || activeAlarmMission != null) ->
                    LimitedRuntimeContent(
                        ringingAlarm = ringingAlarm,
                        activeAlarmMission = activeAlarmMission,
                        activeAlarmMissionLocation = activeAlarmMissionLocation,
                        missionLocationState = missionLocationState,
                        onRingingAlarmDismiss = onRingingAlarmDismiss,
                        onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
                        onActiveAlarmMissionForceEnd = onActiveAlarmMissionForceEnd,
                        onActiveAlarmMissionForceEndHoldStarted =
                            onActiveAlarmMissionForceEndHoldStarted,
                        onActiveAlarmMissionForceEndHoldCancelled =
                            onActiveAlarmMissionForceEndHoldCancelled,
                        onActiveAlarmMissionForceEndHoldCompleted =
                            onActiveAlarmMissionForceEndHoldCompleted,
                    )

                appBootstrapUiState.destination == AppEntryDestination.LogoutCleaning ->
                    LogoutCleanupSurface(
                        errorMessage = appBootstrapUiState.logoutCleanupErrorMessage,
                        onRetry = appBootstrapViewModel::retryLogoutCleanup,
                    )

                shouldShowLimitedRuntimeControls(
                    installation = checkNotNull(appBootstrapUiState.installationEntryState),
                    authSessionState = authSessionState,
                    hasActiveRuntime = ringingAlarm != null || activeAlarmMission != null,
                ) ->
                    LimitedRuntimeContent(
                        ringingAlarm = ringingAlarm,
                        activeAlarmMission = activeAlarmMission,
                        activeAlarmMissionLocation = activeAlarmMissionLocation,
                        missionLocationState = missionLocationState,
                        onRingingAlarmDismiss = onRingingAlarmDismiss,
                        onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
                        onActiveAlarmMissionForceEnd = onActiveAlarmMissionForceEnd,
                        onActiveAlarmMissionForceEndHoldStarted =
                            onActiveAlarmMissionForceEndHoldStarted,
                        onActiveAlarmMissionForceEndHoldCancelled =
                            onActiveAlarmMissionForceEndHoldCancelled,
                        onActiveAlarmMissionForceEndHoldCompleted =
                            onActiveAlarmMissionForceEndHoldCompleted,
                    )

                appBootstrapUiState.destination == AppEntryDestination.Onboarding ->
                    OnboardingRoute(
                        analytics = appContainer.productAnalyticsRecorder,
                        onComplete = appBootstrapViewModel::completeOnboarding,
                        completionEnabled = !appBootstrapUiState.isSaving,
                        completionRetryToken = appBootstrapUiState.onboardingRetryToken,
                    )

                appBootstrapUiState.destination == AppEntryDestination.Login ->
                    AuthEntryContent(
                        appContainer = appContainer,
                        authSessionState = authSessionState,
                        onLoginStarted = appBootstrapViewModel::beginAuthFlow,
                        onAuthFlowEvent = appBootstrapViewModel::handleAuthFlowEvent,
                        onAuthFlowCancelled = appBootstrapViewModel::cancelAuthFlow,
                        authCompletionError = appBootstrapUiState.entryErrorMessage.takeIf {
                            appBootstrapUiState.hasAuthFlowCompletionFailed
                        },
                        onRetryAuthCompletion = appBootstrapViewModel::retryAuthFlowCompletion,
                    )

                appBootstrapUiState.destination == AppEntryDestination.Guide &&
                    termsViewModel.uiState.phase != TermsGatePhase.Allowed ->
                    GuideTermsPreparationSurface(
                        phase = termsViewModel.uiState.phase,
                        onRetry = termsViewModel::retry,
                        termsViewModel = termsViewModel,
                    )

                appBootstrapUiState.destination == AppEntryDestination.Home ||
                    appBootstrapUiState.destination == AppEntryDestination.Guide ->
                    RingoutAppContent(
                        appContainer = appContainer,
                        destinationCurrentLocationCache = checkNotNull(destinationCurrentLocationCache),
                        termsViewModel = termsViewModel,
                        authSessionState = authSessionState,
                        authSessionIdentity = authSessionIdentity,
                        themeMode = appBootstrapUiState.themeMode,
                        isAlarmCreationGuidePending =
                            appBootstrapUiState.destination == AppEntryDestination.Guide,
                        isSavingAlarmCreationGuide = appBootstrapUiState.isSavingAlarmCreationGuide,
                        hasAlarmCreationGuideSaveFailed = appBootstrapUiState.hasAlarmCreationGuideSaveFailed,
                        onAlarmCreationGuideFinished = appBootstrapViewModel::completeAlarmCreationGuide,
                        onExplicitLogout = appBootstrapViewModel::requestExplicitLogout,
                        canPerformAccountAction = { action ->
                            when (action) {
                                MyPageAccountAction.Logout ->
                                    appBootstrapViewModel.canOpenExplicitLogoutConfirmation()
                                MyPageAccountAction.Withdraw -> !currentRuntimeEvidence.value
                            }
                        },
                        logoutBlockedMessage = appBootstrapUiState.logoutCleanupErrorMessage
                            .takeUnless { appBootstrapUiState.isLogoutCleanupPending },
                        onDismissLogoutBlocked = appBootstrapViewModel::dismissLogoutCleanupError,
                        onAuthenticationRequired = appBootstrapViewModel::requestAuthentication,
                        appVersion = appVersion,
                        myPageExtraContent = myPageExtraContent,
                        useSystemLocationPermissionUiOnly = useSystemLocationPermissionUiOnly,
                        ringingAlarm = ringingAlarm,
                        hasExternalRingingAlarm = hasExternalRingingAlarm,
                        pendingArrival = pendingArrival,
                        activeAlarmMission = activeAlarmMission,
                        activeAlarmMissionLocation = activeAlarmMissionLocation,
                        missionLocationState = missionLocationState,
                        onRequestWhenInUseLocation = onRequestWhenInUseLocation,
                        onRequestAlwaysLocation = onRequestAlwaysLocation,
                        onConfirmAlwaysLocationResult = onConfirmAlwaysLocationResult,
                        onRequestTemporaryFullAccuracy = onRequestTemporaryFullAccuracy,
                        onRingingAlarmDismiss = onRingingAlarmDismiss,
                        onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
                        onActiveAlarmMissionForceEnd = onActiveAlarmMissionForceEnd,
                        onActiveAlarmMissionForceEndHoldStarted =
                            onActiveAlarmMissionForceEndHoldStarted,
                        onActiveAlarmMissionForceEndHoldCancelled =
                            onActiveAlarmMissionForceEndHoldCancelled,
                        onActiveAlarmMissionForceEndHoldCompleted =
                            onActiveAlarmMissionForceEndHoldCompleted,
                        pendingRoomActivityPush = pendingRoomActivityPush,
                        onRoomActivityPushHandled = onRoomActivityPushHandled,
                        onThemeModeChange = appBootstrapViewModel::setThemeMode,
                    )

                else -> Unit
            }
        }
    }
}

@Composable
private fun AuthEntryContent(
    appContainer: AppContainer,
    authSessionState: AuthSessionState,
    onLoginStarted: () -> Long,
    onAuthFlowEvent: (AuthFlowEvent) -> AuthFlowEventResult,
    onAuthFlowCancelled: (Long) -> Unit,
    authCompletionError: String?,
    onRetryAuthCompletion: () -> Unit,
) {
    val navigationState = rememberAuthOnlyNavigationState()
    val currentLocationCache = remember { DestinationCurrentLocationCache() }
    val viewModelScopes = rememberNavigationViewModelScopes(
        appContainer = appContainer,
        retainedRoutes = navigationState.backStack,
        destinationCurrentLocationCache = currentLocationCache,
        scopeKey = "auth",
    )
    val authNavigation = rememberAuthNavigation(
        navigationState = navigationState,
        loginViewModel = viewModelScopes.get(AppRoute.Login, LoginViewModel::class),
        signupViewModel = viewModelScopes.get(AppRoute.Login, SignupViewModel::class),
        onLoginStarted = onLoginStarted,
        onAuthFlowEvent = onAuthFlowEvent,
        onAuthFlowCancelled = onAuthFlowCancelled,
    )
    val displayedRoute = navigationState.requestedRoute
    Box(Modifier.fillMaxSize()) {
        RingoutNavHost(
            navigationState = navigationState,
            displayedRoute = displayedRoute,
            viewModelStoreProvider = viewModelScopes.storeProvider,
            isBackBlocked = authNavigation.isBackBlocked(displayedRoute, authSessionState),
            onBack = { route -> authNavigation.onBack(route, displayedRoute, authSessionState) },
            graph = { authGraph(authNavigation = authNavigation, displayedRoute = displayedRoute) },
        )
        authCompletionError?.let { error ->
            StatusSurface(
                message = error,
                isLoading = false,
                onRetry = onRetryAuthCompletion,
            )
        }
    }
}

@Composable
private fun AppBootstrapSurface(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
) = StatusSurface(
    message = errorMessage ?: "앱을 준비하고 있어요",
    isLoading = isLoading,
    onRetry = onRetry.takeIf { !isLoading },
)

@Composable
private fun LogoutCleanupSurface(errorMessage: String?, onRetry: () -> Unit) = StatusSurface(
    message = errorMessage ?: "로그아웃을 마무리하고 있어요",
    isLoading = errorMessage == null,
    onRetry = onRetry.takeIf { errorMessage != null },
)

@Composable
private fun GuideTermsPreparationSurface(
    phase: TermsGatePhase,
    onRetry: () -> Unit,
    termsViewModel: TermsReagreementViewModel,
) {
    val message = when (phase) {
        TermsGatePhase.Checking -> "최신 약관을 확인하고 있어요"
        TermsGatePhase.Offline -> "인터넷에 연결하면 최신 약관 확인을 이어갈 수 있어요"
        TermsGatePhase.CheckFailed -> "최신 약관을 확인하지 못했어요. 다시 시도해 주세요"
        else -> "최신 약관 확인을 마친 뒤 알람 가이드를 시작할 수 있어요"
    }
    StatusSurface(
        message = message,
        isLoading = phase == TermsGatePhase.Checking,
        onRetry = onRetry.takeIf { phase != TermsGatePhase.Checking },
    )
    if (termsViewModel.uiState.blocksService) TermsReagreementRoute(termsViewModel)
}

@Composable
private fun StatusSurface(
    message: String,
    isLoading: Boolean,
    onRetry: (() -> Unit)?,
) = Box(
    modifier = Modifier.fillMaxSize().background(Color.Black),
    contentAlignment = Alignment.Center,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        if (isLoading) CircularProgressIndicator()
        Text(message)
        if (onRetry != null) Button(onClick = onRetry) { Text("다시 시도") }
    }
}

@Composable
private fun LimitedRuntimeContent(
    ringingAlarm: AlarmRingingUiState?,
    activeAlarmMission: ActiveAlarmMission?,
    activeAlarmMissionLocation: ActiveAlarmMissionLocation?,
    missionLocationState: MissionLocationState,
    onRingingAlarmDismiss: (String) -> Unit,
    onActiveAlarmMissionExpired: () -> Unit,
    onActiveAlarmMissionForceEnd: suspend (occurrenceId: String) -> ForceEndMissionResult,
    onActiveAlarmMissionForceEndHoldStarted: (occurrenceId: String) -> Unit,
    onActiveAlarmMissionForceEndHoldCancelled:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit,
    onActiveAlarmMissionForceEndHoldCompleted:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit,
) {
    val forceEndViewModel = viewModel { ForceEndMissionViewModel(createSavedStateHandle()) }
    val forceEndState by forceEndViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(forceEndViewModel) {
        forceEndViewModel.resumePending(onActiveAlarmMissionForceEnd)
    }
    Box(Modifier.fillMaxSize()) {
        when {
            ringingAlarm != null -> AlarmRingingRoute(
                limitMinutes = ringingAlarm.limitMinutes,
                destinationName = ringingAlarm.destinationName,
                onDismissAndNavigateClick = { onRingingAlarmDismiss(ringingAlarm.id) },
            )

            activeAlarmMission != null -> ActiveAlarmTrackingScreen(
                mission = activeAlarmMission,
                currentLocation = activeAlarmMissionLocation,
                locationState = missionLocationState,
                onBackClick = {},
                onForceEndClick = { forceEndViewModel.request(it, onActiveAlarmMissionForceEnd) },
                onExpired = onActiveAlarmMissionExpired,
                isForceEndEnabled = forceEndState.phase == ForceEndPhase.Idle,
                onForceEndHoldStarted = onActiveAlarmMissionForceEndHoldStarted,
                onForceEndHoldCancelled = onActiveAlarmMissionForceEndHoldCancelled,
                onForceEndHoldCompleted = onActiveAlarmMissionForceEndHoldCompleted,
            )

            else -> StatusSurface(
                message = "진행 중인 알람을 정리하고 있어요",
                isLoading = true,
                onRetry = null,
            )
        }
        if (ringingAlarm == null) {
            ForceEndMissionStatus(
                uiState = forceEndState,
                onConfirm = forceEndViewModel::dismiss,
                onRetry = { forceEndViewModel.retry(onActiveAlarmMissionForceEnd) },
                onDismiss = forceEndViewModel::dismiss,
            )
        }
    }
}

@Composable
private fun RingoutAppContent(
    appContainer: AppContainer,
    destinationCurrentLocationCache: DestinationCurrentLocationCache,
    termsViewModel: TermsReagreementViewModel,
    authSessionState: AuthSessionState,
    authSessionIdentity: Any?,
    themeMode: ThemeMode,
    isAlarmCreationGuidePending: Boolean,
    isSavingAlarmCreationGuide: Boolean,
    hasAlarmCreationGuideSaveFailed: Boolean,
    onAlarmCreationGuideFinished: () -> Unit,
    onExplicitLogout: () -> Unit,
    canPerformAccountAction: (MyPageAccountAction) -> Boolean,
    logoutBlockedMessage: String?,
    onDismissLogoutBlocked: () -> Unit,
    onAuthenticationRequired: () -> Unit,
    appVersion: String,
    myPageExtraContent: (@Composable (ThemeMode) -> Unit)?,
    useSystemLocationPermissionUiOnly: Boolean,
    ringingAlarm: AlarmRingingUiState?,
    hasExternalRingingAlarm: Boolean,
    pendingArrival: PendingArrivalUiState,
    activeAlarmMission: ActiveAlarmMission?,
    activeAlarmMissionLocation: ActiveAlarmMissionLocation?,
    missionLocationState: MissionLocationState,
    onRequestWhenInUseLocation: () -> Unit,
    onRequestAlwaysLocation: () -> Unit,
    onConfirmAlwaysLocationResult: () -> Unit,
    onRequestTemporaryFullAccuracy: () -> Unit,
    onRingingAlarmDismiss: (String) -> Unit,
    onActiveAlarmMissionExpired: () -> Unit,
    onActiveAlarmMissionForceEnd: suspend (occurrenceId: String) -> ForceEndMissionResult,
    onActiveAlarmMissionForceEndHoldStarted: (occurrenceId: String) -> Unit,
    onActiveAlarmMissionForceEndHoldCancelled:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit,
    onActiveAlarmMissionForceEndHoldCompleted:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit,
    pendingRoomActivityPush: RoomActivityPushRequest?,
    onRoomActivityPushHandled: (requestId: String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val alarmCreationGuideViewModel = viewModel { AlarmCreationGuideViewModel() }
    val forceEndViewModel = viewModel { ForceEndMissionViewModel(createSavedStateHandle()) }
    val forceEndState by forceEndViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(forceEndViewModel) {
        forceEndViewModel.resumePending(onActiveAlarmMissionForceEnd)
    }
    val productAnalyticsRecorder = appContainer.productAnalyticsRecorder
    val navigationState = rememberAppNavigationState(authSessionIdentity)
    // iOS 알람 울림 이동 정책 이전이 끝날 때까지 플랫폼 울림 상태를 현재 백스택보다 우선 표시한다.
    val displayedRoute = ringingAlarm
        ?.let { alarm -> AppRoute.AlarmRinging(alarm.id) }
        ?: navigationState.requestedRoute
    HomeNotificationPermissionEffect(
        authSessionState = authSessionState,
        displayedRoute = displayedRoute,
        isAlarmCreationGuidePending = isAlarmCreationGuidePending,
        isServiceBlocked = termsViewModel.uiState.blocksService || forceEndState.phase != ForceEndPhase.Idle ||
            !pendingArrival.isLoaded || pendingArrival.result != null,
    )
    val retainedRoutes = navigationState.retainedRoutes(displayedRoute)
    val viewModelScopes = rememberNavigationViewModelScopes(
        appContainer,
        retainedRoutes,
        destinationCurrentLocationCache,
        sessionIdentity = authSessionIdentity,
        explicitLogout = onExplicitLogout,
    )
    val homeViewModel = viewModelScopes.get(AppRoute.Home, HomeViewModel::class)
    val socialRoomListViewModel = if (AppRoute.Social in retainedRoutes) {
        viewModelScopes.get(AppRoute.Social, RoomListViewModel::class)
    } else {
        null
    }
    val roomHomeViewModels = remember(retainedRoutes, viewModelScopes) {
        retainedRoutes.filterIsInstance<AppRoute.RoomHome>().map { route ->
            viewModelScopes.get(route, RoomHomeViewModel::class)
        }
    }
    val roomActivityViewModels = remember(retainedRoutes, viewModelScopes) {
        retainedRoutes.filterIsInstance<AppRoute.RoomActivity>().map { route ->
            viewModelScopes.get(route, RoomActivityViewModel::class)
        }
    }
    val myPageViewModel = if (AppRoute.MyPage in retainedRoutes) {
        viewModelScopes.get(AppRoute.MyPage, MyPageViewModel::class)
    } else {
        null
    }
    val editorRoute = navigationState.editorRoute
    val alarmEditorNavigation = if (editorRoute != null) {
        rememberAlarmEditorNavigation(
            navigationState,
            viewModelScopes.get(editorRoute, AlarmSetupViewModel::class),
            viewModelScopes.get(editorRoute, DestinationViewModel::class),
        )
    } else {
        null
    }
    val roomEditRoute = displayedRoute as? AppRoute.RoomEdit
    val roomEditViewModel = roomEditRoute?.let {
        viewModelScopes.get(it, RoomEditViewModel::class)
    }
    val roomEditSuccessfulUpdate = roomEditViewModel?.uiState?.successfulUpdate
    LaunchedEffect(
        roomEditViewModel,
        roomEditRoute,
        roomEditSuccessfulUpdate?.completionId,
        authSessionState,
        authSessionIdentity,
    ) {
        val route = roomEditRoute ?: return@LaunchedEffect
        val viewModel = roomEditViewModel ?: return@LaunchedEffect
        val completionId = roomEditSuccessfulUpdate?.completionId ?: return@LaunchedEffect
        val completion = viewModel.consumeSuccessfulUpdate(completionId) ?: return@LaunchedEffect
        if (
            !navigationState.isCurrentRoute(route) ||
            authSessionState != AuthSessionState.Authenticated ||
            completion.sessionIdentity !== authSessionIdentity ||
            completion.result.roomId.toString() != route.roomId
        ) return@LaunchedEffect

        val homeRoute = AppRoute.RoomHome(route.roomId)
        if (navigationState.backStack.dropLast(1).lastOrNull() != homeRoute) return@LaunchedEffect
        viewModelScopes.get(homeRoute, RoomHomeViewModel::class)
            .onRoomUpdated(completion.result, completion.sessionIdentity)
        viewModelScopes.get(AppRoute.Social, RoomListViewModel::class)
            .onRoomUpdated(completion.result, completion.sessionIdentity)
        navigationState.popBackStack(route)
    }
    AuthSessionCoordinator(
        authSession = appContainer.authSession,
        authSessionState = authSessionState,
        pushTokenSource = appContainer.pushTokenSource,
        pushTokenRegistrationReadiness = appContainer.pushTokenRegistrationReadiness,
        pushTokenRegistrar = appContainer.pushTokenRegistrar,
        myPageViewModel = myPageViewModel,
        destinationViewModel = alarmEditorNavigation?.destinationViewModel,
        roomListViewModel = socialRoomListViewModel,
        roomHomeViewModels = roomHomeViewModels,
        roomActivityViewModels = roomActivityViewModels,
    )
    val roomMutationState = socialRoomListViewModel?.mutationState
    LaunchedEffect(roomMutationState?.operationId, roomMutationState?.isSuccessful) {
        val state = roomMutationState ?: return@LaunchedEffect
        if (!state.isSuccessful) return@LaunchedEffect
        val roomListViewModel = socialRoomListViewModel
        val success = roomListViewModel.consumeSuccessfulMutation(state.operationId)
            ?: return@LaunchedEffect
        if (!roomListViewModel.isCurrentMutationSource(success.source.entryId)) return@LaunchedEffect
        val sourceRoute = when (success.source.type) {
            RoomMutationType.Create -> AppRoute.RoomCreate
            RoomMutationType.Join -> success.source.roomId?.let(AppRoute::RoomDetail)
                ?: return@LaunchedEffect
        }
        navigationState.navigateToRoomHomeFrom(sourceRoute, success.roomId)
    }
    val alarmController = rememberAppAlarmController(
        navigationState = navigationState,
        editorRoute = editorRoute,
        alarmSetupViewModel = alarmEditorNavigation?.alarmSetupViewModel,
        homeViewModel = homeViewModel,
    )
    AppRuntimeCoordinator(
        authSessionState = authSessionState,
        navigationState = navigationState,
        displayedRoute = displayedRoute,
        pendingArrivalOccurrenceId = pendingArrival.result?.occurrenceId,
        isArrivalStateLoaded = pendingArrival.isLoaded,
        hasExternalRingingAlarm = hasExternalRingingAlarm,
        pendingRoomActivityPush = pendingRoomActivityPush,
        canHandleRoomActivityPush = !isAlarmCreationGuidePending &&
            !termsViewModel.uiState.blocksService &&
            activeAlarmMission == null && ringingAlarm == null,
        onRoomActivityPushHandled = onRoomActivityPushHandled,
        themeMode = themeMode,
        activeMissionOccurrenceId = activeAlarmMission?.occurrenceId,
        homeViewModel = homeViewModel,
        alarmEditorNavigation = alarmEditorNavigation,
        alarmController = alarmController,
        missionLocationState = missionLocationState,
        useSystemLocationPermissionUiOnly = useSystemLocationPermissionUiOnly,
        onRequestWhenInUseLocation = onRequestWhenInUseLocation,
        onRequestAlwaysLocation = onRequestAlwaysLocation,
        onConfirmAlwaysLocationResult = onConfirmAlwaysLocationResult,
        onRequestTemporaryFullAccuracy = onRequestTemporaryFullAccuracy,
    )

    val roomHomeMenuAction = if (displayedRoute is AppRoute.RoomHome) {
        viewModelScopes
            .get(displayedRoute, RoomHomeViewModel::class)
            .uiState
            .collectAsStateWithLifecycle()
            .value
            .menuActionState
    } else {
        null
    }
    val onAddAlarm: () -> Unit = {
        viewModelScopes.createAlarmEditorNavigation(navigationState, AppRoute.AddAlarm)
            .startCreating(
                initialTime = currentLocalClockSnapshot().to24HourTimeString(),
            )
    }
    val guideSurface = when (displayedRoute) {
        AppRoute.Home -> AlarmCreationGuideSurface.Home
        AppRoute.AddAlarm -> AlarmCreationGuideSurface.Editor
        else -> AlarmCreationGuideSurface.Other
    }
    AlarmCreationGuideRoute(
        viewModel = alarmCreationGuideViewModel,
        isPending = isAlarmCreationGuidePending,
        surface = guideSurface,
        canPresent = !termsViewModel.uiState.blocksService &&
            displayedRoute !is AppRoute.ArrivalShare && displayedRoute !is AppRoute.RecordShare && displayedRoute !is AppRoute.Arrival &&
            pendingArrival.isLoaded && pendingArrival.result == null &&
            forceEndState.phase == ForceEndPhase.Idle &&
            authSessionState != AuthSessionState.Restoring &&
            authSessionState != AuthSessionState.ReauthenticationRequired &&
            (displayedRoute != AppRoute.Home || !homeViewModel.uiState.isLoading),
        isAlarmSaving = alarmEditorNavigation?.alarmSetupViewModel?.uiState?.isSaveInProgress == true,
        isFinishing = isSavingAlarmCreationGuide,
        finishFailed = hasAlarmCreationGuideSaveFailed,
        onOpenEditor = onAddAlarm,
        onFinish = onAlarmCreationGuideFinished,
        onBackFromEditor = {
            if (!isSavingAlarmCreationGuide && forceEndState.phase == ForceEndPhase.Idle) {
                alarmCreationGuideViewModel.interrupt()
                navigationState.navigate(AppRoute.Home)
            }
        },
    ) {
        RingoutNavHost(
            navigationState = navigationState,
            displayedRoute = displayedRoute,
            viewModelStoreProvider = viewModelScopes.storeProvider,
            modifier = Modifier.fillMaxSize(),
            isBackBlocked = displayedRoute is AppRoute.AlarmRinging ||
                    roomHomeMenuAction?.blocksNavigationBack == true ||
                    roomEditViewModel?.uiState?.isSaving == true ||
                    alarmEditorNavigation?.isBackBlocked(displayedRoute) == true,
            onBack = { route ->
                when (route) {
                    AppRoute.AddAlarm,
                    is AppRoute.EditAlarm,
                    is AppRoute.Destination,
                    AppRoute.AlarmSound,
                    -> alarmEditorNavigation?.onBack(route, displayedRoute)
                    AppRoute.RoomCreate -> viewModelScopes
                        .get(route, RoomCreateViewModel::class)
                        .onBack { navigationState.popBackStack(route) }
                    is AppRoute.RoomEdit -> {
                        if (!viewModelScopes.get(route, RoomEditViewModel::class).uiState.isSaving) {
                            navigationState.popBackStack(route)
                        }
                    }
                    is AppRoute.RoomMemberManagement -> {
                        viewModelScopes
                            .get(AppRoute.RoomHome(route.roomId), RoomHomeViewModel::class)
                            .refreshRoomDetails()
                        navigationState.popBackStack(route)
                    }
                    is AppRoute.ActiveAlarmTracking -> navigationState.navigate(AppRoute.Home)
                    is AppRoute.AlarmRinging -> Unit
                    // The registered ArrivalRoute back action persists acknowledgement first.
                    is AppRoute.Arrival -> Unit
                    else -> navigationState.popBackStack(route)
                }
            },
            graph = {
                homeGraph(
                    authSessionState = authSessionState,
                    sessionIdentity = authSessionIdentity,
                    networkMonitor = appContainer.networkMonitor,
                    memberRepository = appContainer.memberRepository,
                    viewModelScopes = viewModelScopes,
                    navigationState = navigationState,
                    homeViewModel = homeViewModel,
                    myPageViewModel = myPageViewModel,
                    themeMode = themeMode,
                    appVersion = appVersion,
                    myPageExtraContent = myPageExtraContent,
                    alarmController = alarmController,
                    activeAlarmMission = activeAlarmMission,
                    onThemeModeChange = onThemeModeChange,
                    canPerformAccountAction = canPerformAccountAction,
                    logoutBlockedMessage = logoutBlockedMessage,
                    onDismissLogoutBlocked = onDismissLogoutBlocked,
                    onAddAlarm = onAddAlarm,
                    onEditAlarm = { request ->
                        viewModelScopes.createAlarmEditorNavigation(
                            navigationState,
                            AppRoute.EditAlarm(request.id),
                        ).startEditing(request)
                    },
                    onAuthenticationRequired = onAuthenticationRequired,
                    onActiveAlarmMissionClick = {
                        activeAlarmMission?.occurrenceId?.let { occurrenceId ->
                            navigationState.navigate(AppRoute.ActiveAlarmTracking(occurrenceId))
                        }
                    },
                    onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
                    onJoinRoom = { source -> socialRoomListViewModel?.joinRoom(source) },
                    onRoomCreateDraft = { source, input ->
                        socialRoomListViewModel?.createRoom(source, input)
                    },
                )
                alarmRuntimeGraph(
                    navigationState = navigationState,
                    ringingAlarm = ringingAlarm,
                    activeAlarmMission = activeAlarmMission,
                    activeAlarmMissionLocation = activeAlarmMissionLocation,
                    missionLocationState = missionLocationState,
                    onRingingAlarmDismiss = onRingingAlarmDismiss,
                    onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
                    onActiveAlarmMissionForceEnd = { forceEndViewModel.request(it, onActiveAlarmMissionForceEnd) },
                    isForceEndEnabled = forceEndState.phase == ForceEndPhase.Idle,
                    onActiveAlarmMissionForceEndHoldStarted =
                        onActiveAlarmMissionForceEndHoldStarted,
                    onActiveAlarmMissionForceEndHoldCancelled =
                        onActiveAlarmMissionForceEndHoldCancelled,
                    onActiveAlarmMissionForceEndHoldCompleted =
                        onActiveAlarmMissionForceEndHoldCompleted,
                )
                arrivalGraph(
                    storeProvider = viewModelScopes.storeProvider,
                    navigationState = navigationState,
                    repository = appContainer.arrivalResultRepository,
                    missionHistoryRepository = appContainer.missionHistoryRepository,
                    pendingArrival = pendingArrival,
                    hasActiveRuntime = hasExternalRingingAlarm || ringingAlarm != null || activeAlarmMission != null,
                )
                if (alarmEditorNavigation != null) {
                    alarmEditorGraph(
                        navigation = alarmEditorNavigation,
                        displayedRoute = displayedRoute,
                        authSessionState = authSessionState,
                        productAnalyticsRecorder = productAnalyticsRecorder,
                        networkMonitor = appContainer.networkMonitor,
                    )
                }
            },
        )
    }
    // 알람 울림과 진행 중 미션의 필수 조작은 기존 우선순위를 유지한다.
    if (forceEndState.phase == ForceEndPhase.Idle &&
        displayedRoute !is AppRoute.AlarmRinging && displayedRoute !is AppRoute.ActiveAlarmTracking &&
        displayedRoute !is AppRoute.ArrivalShare && displayedRoute !is AppRoute.RecordShare && displayedRoute !is AppRoute.Arrival
    ) {
        TermsReagreementRoute(termsViewModel)
    }
    if (ringingAlarm == null) {
        ForceEndMissionStatus(
            uiState = forceEndState,
            onConfirm = forceEndViewModel::dismiss,
            onRetry = { forceEndViewModel.retry(onActiveAlarmMissionForceEnd) },
            onDismiss = forceEndViewModel::dismiss,
        )
    }

}
