package com.joon.ringout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.DefaultMissionLocationState
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.di.AppContainer
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.app.AuthSessionCoordinator
import com.joon.ringout.presentation.app.ReauthenticationCoordinator
import com.joon.ringout.presentation.app.AppRuntimeCoordinator
import com.joon.ringout.presentation.app.rememberAppAlarmController
import com.joon.ringout.presentation.appbootstrap.AppBootstrapViewModel
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.login.LoginViewModel
import com.joon.ringout.presentation.signup.SignupViewModel
import com.joon.ringout.presentation.navigation.authGraph
import com.joon.ringout.presentation.navigation.rememberAuthNavigation
import com.joon.ringout.presentation.mypage.MyPageViewModel
import com.joon.ringout.presentation.onboarding.OnboardingRoute
import com.joon.ringout.presentation.ringing.AlarmRingingUiState
import com.joon.ringout.presentation.navigation.AppRoute
import com.joon.ringout.presentation.navigation.RingoutNavHost
import com.joon.ringout.presentation.navigation.alarmRuntimeGraph
import com.joon.ringout.presentation.navigation.alarmEditorGraph
import com.joon.ringout.presentation.navigation.rememberAlarmEditorNavigation
import com.joon.ringout.presentation.navigation.homeGraph
import com.joon.ringout.presentation.navigation.rememberAppNavigationState
import com.joon.ringout.presentation.navigation.rememberNavigationViewModelScopes
import com.joon.ringout.presentation.currentLocalClockSnapshot
import com.joon.ringout.presentation.to24HourTimeString

@Composable
fun App(
    appContainer: AppContainer,
    appVersion: String = "",
    useSystemLocationPermissionUiOnly: Boolean = false,
    ringingAlarm: AlarmRingingUiState? = null,
    activeAlarmMission: ActiveAlarmMission? = null,
    activeAlarmMissionLocation: ActiveAlarmMissionLocation? = null,
    missionLocationState: MissionLocationState = DefaultMissionLocationState,
    onRequestWhenInUseLocation: () -> Unit = {},
    onRequestAlwaysLocation: () -> Unit = {},
    onConfirmAlwaysLocationResult: () -> Unit = {},
    onRequestTemporaryFullAccuracy: () -> Unit = {},
    onRingingAlarmDismiss: (String) -> Unit = {},
    onActiveAlarmMissionExpired: () -> Unit = {},
    onActiveAlarmMissionForceEnd: (occurrenceId: String) -> Unit = { _ -> },
    onActiveAlarmMissionForceEndHoldStarted: (occurrenceId: String) -> Unit = { _ -> },
    onActiveAlarmMissionForceEndHoldCancelled:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit = { _, _ -> },
    onActiveAlarmMissionForceEndHoldCompleted:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit = { _, _ -> },
) {
    val appBootstrapViewModel = viewModel {
        AppBootstrapViewModel(
            repository = appContainer.appPreferencesRepository,
            onboardingAnalytics = appContainer.productAnalyticsRecorder,
            systemThemeModeReader = appContainer.systemThemeModeReader,
        )
    }
    val appBootstrapUiState = appBootstrapViewModel.uiState

    if (!appBootstrapUiState.isReady) {
        AppBootstrapSurface()
        return
    }

    RingoutTheme(themeMode = appBootstrapUiState.themeMode) {
        when (appBootstrapUiState.destination) {
            AppEntryDestination.Onboarding ->
                OnboardingRoute(
                    appContainer = appContainer,
                    missionLocationState = missionLocationState,
                    useSystemLocationPermissionUiOnly = useSystemLocationPermissionUiOnly,
                    onRequestWhenInUseLocation = onRequestWhenInUseLocation,
                    onRequestAlwaysLocation = onRequestAlwaysLocation,
                    onConfirmAlwaysLocationResult = onConfirmAlwaysLocationResult,
                    onRequestTemporaryFullAccuracy = onRequestTemporaryFullAccuracy,
                    onComplete = appBootstrapViewModel::completeOnboarding,
                    completionEnabled = !appBootstrapUiState.isSaving,
                    completionRetryToken = appBootstrapUiState.onboardingRetryToken,
                )

            AppEntryDestination.Home ->
                RingoutAppContent(
                    appContainer = appContainer,
                    themeMode = appBootstrapUiState.themeMode,
                    appVersion = appVersion,
                    useSystemLocationPermissionUiOnly = useSystemLocationPermissionUiOnly,
                    ringingAlarm = ringingAlarm,
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
                    onThemeModeChange = appBootstrapViewModel::setThemeMode,
                )

            null -> Unit
        }
    }
}

@Composable
private fun AppBootstrapSurface() = Box(
    modifier = Modifier
        .fillMaxSize()
        .background(Color.Black),
)

@Composable
private fun RingoutAppContent(
    appContainer: AppContainer,
    themeMode: ThemeMode,
    appVersion: String,
    useSystemLocationPermissionUiOnly: Boolean,
    ringingAlarm: AlarmRingingUiState?,
    activeAlarmMission: ActiveAlarmMission?,
    activeAlarmMissionLocation: ActiveAlarmMissionLocation?,
    missionLocationState: MissionLocationState,
    onRequestWhenInUseLocation: () -> Unit,
    onRequestAlwaysLocation: () -> Unit,
    onConfirmAlwaysLocationResult: () -> Unit,
    onRequestTemporaryFullAccuracy: () -> Unit,
    onRingingAlarmDismiss: (String) -> Unit,
    onActiveAlarmMissionExpired: () -> Unit,
    onActiveAlarmMissionForceEnd: (occurrenceId: String) -> Unit,
    onActiveAlarmMissionForceEndHoldStarted: (occurrenceId: String) -> Unit,
    onActiveAlarmMissionForceEndHoldCancelled:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit,
    onActiveAlarmMissionForceEndHoldCompleted:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val productAnalyticsRecorder = appContainer.productAnalyticsRecorder
    val authSessionState by appContainer.authSession.state.collectAsStateWithLifecycle()
    val navigationState = rememberAppNavigationState()
    // iOS 알람 울림 이동 정책 이전이 끝날 때까지 플랫폼 울림 상태를 현재 백스택보다 우선 표시한다.
    val displayedRoute = ringingAlarm
        ?.let { alarm -> AppRoute.AlarmRinging(alarm.id) }
        ?: navigationState.requestedRoute
    val retainedRoutes = navigationState.retainedRoutes(displayedRoute)
    val viewModelScopes = rememberNavigationViewModelScopes(appContainer, retainedRoutes)
    val homeViewModel = viewModelScopes.get(AppRoute.Home, HomeViewModel::class)
    val myPageViewModel = if (AppRoute.MyPage in retainedRoutes) {
        viewModelScopes.get(AppRoute.MyPage, MyPageViewModel::class)
    } else {
        null
    }
    val authNavigation = if (AppRoute.Login in retainedRoutes) {
        rememberAuthNavigation(
            navigationState,
            viewModelScopes.get(AppRoute.Login, LoginViewModel::class),
            viewModelScopes.get(AppRoute.Login, SignupViewModel::class),
        )
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
    AuthSessionCoordinator(
        authRepository = appContainer.authRepository,
        authSessionState = authSessionState,
        myPageViewModel = myPageViewModel,
        destinationViewModel = alarmEditorNavigation?.destinationViewModel,
    )
    ReauthenticationCoordinator(
        authSessionState = authSessionState,
        navigationState = navigationState,
        homeViewModel = homeViewModel,
        signupViewModel = authNavigation?.signupViewModel,
        alarmSetupViewModel = alarmEditorNavigation?.alarmSetupViewModel,
        myPageViewModel = myPageViewModel,
        destinationViewModel = alarmEditorNavigation?.destinationViewModel,
    )
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

    RingoutNavHost(
        navigationState = navigationState,
        displayedRoute = displayedRoute,
        viewModelStoreProvider = viewModelScopes.storeProvider,
        modifier = Modifier.fillMaxSize(),
        isBackBlocked =
            displayedRoute is AppRoute.AlarmRinging ||
                alarmEditorNavigation?.isBackBlocked(displayedRoute) == true ||
                authNavigation?.isBackBlocked(displayedRoute, authSessionState) == true,
        onBack = { route ->
            when (route) {
                AppRoute.Login,
                AppRoute.TermsAgreement,
                -> authNavigation?.onBack(route, displayedRoute, authSessionState)
                AppRoute.AddAlarm,
                is AppRoute.EditAlarm,
                is AppRoute.Destination,
                AppRoute.AlarmSound,
                -> alarmEditorNavigation?.onBack(route, displayedRoute)
                is AppRoute.ActiveAlarmTracking -> navigationState.navigate(AppRoute.Home)
                is AppRoute.AlarmRinging -> Unit
                else -> navigationState.popBackStack(route)
            }
        },
        graph = {
            homeGraph(
                authSessionState = authSessionState,
                memberRepository = appContainer.memberRepository,
                viewModelScopes = viewModelScopes,
                navigationState = navigationState,
                homeViewModel = homeViewModel,
                myPageViewModel = myPageViewModel,
                themeMode = themeMode,
                appVersion = appVersion,
                alarmController = alarmController,
                activeAlarmMission = activeAlarmMission,
                onThemeModeChange = onThemeModeChange,
                onAddAlarm = {
                    viewModelScopes.createAlarmEditorNavigation(navigationState, AppRoute.AddAlarm)
                        .startCreating(
                            initialTime = currentLocalClockSnapshot().to24HourTimeString(),
                        )
                },
                onEditAlarm = { request ->
                    viewModelScopes.createAlarmEditorNavigation(
                        navigationState,
                        AppRoute.EditAlarm(request.id),
                    ).startEditing(request)
                },
                onActiveAlarmMissionClick = {
                    activeAlarmMission?.occurrenceId?.let { occurrenceId ->
                        navigationState.navigate(AppRoute.ActiveAlarmTracking(occurrenceId))
                    }
                },
                onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
            )
            alarmRuntimeGraph(
                navigationState = navigationState,
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
            if (authNavigation != null) {
                authGraph(
                    authNavigation = authNavigation,
                    displayedRoute = displayedRoute,
                    authSessionState = authSessionState,
                )
            }
            if (alarmEditorNavigation != null) {
                alarmEditorGraph(
                    navigation = alarmEditorNavigation,
                    displayedRoute = displayedRoute,
                    authSessionState = authSessionState,
                    productAnalyticsRecorder = productAnalyticsRecorder,
                )
            }
        },
    )
}
