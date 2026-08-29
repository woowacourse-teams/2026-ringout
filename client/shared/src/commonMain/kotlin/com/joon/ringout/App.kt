package com.joon.ringout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.DefaultMissionLocationState
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.alarm.rememberAlarmController
import com.joon.ringout.di.AppContainer
import com.joon.ringout.presentation.activemission.ActiveAlarmTrackingScreen
import com.joon.ringout.presentation.activemission.components.MissionLocationPermissionDialog
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.login.LoginViewModel
import com.joon.ringout.presentation.appbootstrap.AppBootstrapViewModel
import com.joon.ringout.presentation.common.AppMessageSource
import com.joon.ringout.presentation.common.resolveAppMessage
import com.joon.ringout.presentation.common.component.AppMessageHost
import com.joon.ringout.presentation.onboarding.OnboardingRoute
import com.joon.ringout.presentation.ringing.AlarmRingingScreen
import com.joon.ringout.presentation.ringing.AlarmRingingUiState
import com.joon.ringout.presentation.signup.SignupViewModel
import com.joon.ringout.presentation.mypage.MyPageViewModel
import com.joon.ringout.presentation.mypage.model.MyPageAccountAction
import com.joon.ringout.presentation.mypage.model.MyPageAccountActionState
import com.joon.ringout.presentation.navigation.AppRoute
import com.joon.ringout.presentation.navigation.ReauthenticationNavigationEffect
import com.joon.ringout.presentation.navigation.AlarmEditorNavigationEffects
import com.joon.ringout.presentation.navigation.RingoutNavHost
import com.joon.ringout.presentation.navigation.alarmEditorGraph
import com.joon.ringout.presentation.navigation.authGraph
import com.joon.ringout.presentation.navigation.rememberAlarmEditorNavigation
import com.joon.ringout.presentation.navigation.rememberAuthNavigation
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
            repository = appContainer.appPreferencesRepository
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
    val authSession = appContainer.authSession
    val authRepository = appContainer.authRepository
    val memberRepository = appContainer.memberRepository
    val authSessionState by authSession.state.collectAsState()
    val navigationState = rememberAppNavigationState()
    val screen = resolveAppScreen(
        requestedScreen = navigationState.requestedScreen,
        hasRingingAlarm = ringingAlarm != null,
        hasActiveAlarmMission = activeAlarmMission != null,
        authSessionState = authSessionState,
    )
    val retainedRoutes = navigationState.retainedRoutes(screen)
    val viewModelScopes = rememberNavigationViewModelScopes(appContainer, retainedRoutes)
    val homeViewModel = viewModelScopes.get(AppRoute.Home, HomeViewModel::class)
    val homeUiState = homeViewModel.uiState
    val myPageViewModel = if (AppRoute.MyPage in retainedRoutes) {
        viewModelScopes.get(AppRoute.MyPage, MyPageViewModel::class)
    } else {
        null
    }
    val myPageUiState = myPageViewModel?.uiState
    val authNavigation = if (AppRoute.Login in retainedRoutes) {
        rememberAuthNavigation(
            navigationState,
            viewModelScopes.get(AppRoute.Login, LoginViewModel::class),
            viewModelScopes.get(AppRoute.Login, SignupViewModel::class),
        )
    } else {
        null
    }
    val signupViewModel = authNavigation?.signupViewModel
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
    val alarmSetupViewModel = alarmEditorNavigation?.alarmSetupViewModel
    val alarmSetupUiState = alarmSetupViewModel?.uiState
    val destinationViewModel = alarmEditorNavigation?.destinationViewModel
    val destinationUiState = destinationViewModel?.uiState

    LaunchedEffect(authSessionState, myPageViewModel, destinationViewModel) {
        when (authSessionState) {
            AuthSessionState.Restoring -> myPageViewModel?.onSessionRestoring()
            AuthSessionState.Unauthenticated,
            AuthSessionState.ReauthenticationRequired,
            -> {
                myPageViewModel?.onLoggedOut()
                destinationViewModel?.onLoggedOut()
            }

            AuthSessionState.Authenticated -> myPageViewModel?.onAuthenticated()
        }
    }
    var handledActiveAlarmOccurrenceId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(authRepository) {
        authRepository.restoreSession()
    }
    ReauthenticationNavigationEffect(
        authSessionState = authSessionState,
        navigationState = navigationState,
        homeViewModel = homeViewModel,
        signupViewModel = signupViewModel,
        alarmSetupViewModel = alarmSetupViewModel,
        myPageViewModel = myPageViewModel,
        destinationViewModel = destinationViewModel,
    )
    val completedAccountAction =
        myPageUiState?.accountAction as? MyPageAccountActionState.Completed
    LaunchedEffect(myPageViewModel, completedAccountAction?.eventId) {
        val completed = completedAccountAction ?: return@LaunchedEffect
        signupViewModel?.resetSignup()
        navigationState.navigate(
            when (completed.action) {
                MyPageAccountAction.Logout -> AppScreen.Login
                MyPageAccountAction.Withdraw -> AppScreen.MyPage
            },
        )
        myPageViewModel.consumeAccountActionCompletedEvent(completed.eventId)
    }
    if (alarmEditorNavigation != null) {
        AlarmEditorNavigationEffects(navigation = alarmEditorNavigation, screen = screen)
    }
    SystemBarAppearanceEffect(
        themeMode = if (
            screen == AppScreen.ActiveAlarmTracking ||
            screen == AppScreen.AlarmRinging
        ) {
            ThemeMode.Dark
        } else {
            themeMode
        },
    )
    val alarmController = rememberAlarmController(
        onSaveCompleted = { request ->
            if (
                editorRoute != null && navigationState.editorRoute == editorRoute &&
                alarmSetupViewModel?.onSaveCompleted(request) == true
            ) {
                navigationState.navigate(AppRoute.Home)
            }
        },
        onSaveError = { request, message ->
            if (navigationState.editorRoute == editorRoute) {
                alarmSetupViewModel?.onSaveError(request, message)
            }
        },
        onError = homeViewModel::showError,
    )
    val performAlarmSetupCommand: (AlarmSetupViewModel.Command?) -> Unit = { command ->
        when (command) {
            is AlarmSetupViewModel.Command.ScheduleAlarm ->
                alarmController.schedule(command.request)

            AlarmSetupViewModel.Command.RequestWhenInUseLocation ->
                onRequestWhenInUseLocation()

            AlarmSetupViewModel.Command.RequestAlwaysLocation ->
                onRequestAlwaysLocation()

            AlarmSetupViewModel.Command.ConfirmAlwaysLocationResult ->
                onConfirmAlwaysLocationResult()

            AlarmSetupViewModel.Command.RequestTemporaryFullAccuracy ->
                onRequestTemporaryFullAccuracy()

            null -> Unit
        }
    }
    LaunchedEffect(
        alarmSetupViewModel,
        alarmSetupUiState?.pendingSaveRequest?.id,
        alarmSetupUiState?.isScheduling,
        missionLocationState.revision,
        screen,
    ) {
        performAlarmSetupCommand(
            alarmSetupViewModel?.onLocationStateChanged(
                locationState = missionLocationState,
                useSystemPermissionUiOnly = useSystemLocationPermissionUiOnly,
                canProcessSave =
                    screen != AppScreen.AlarmRinging &&
                        authSessionState != AuthSessionState.ReauthenticationRequired,
            ),
        )
    }

    if (
        !useSystemLocationPermissionUiOnly &&
        canShowAppDialog(screen, authSessionState)
    ) {
        MissionLocationPermissionDialog(
            decision = alarmSetupViewModel?.permissionDialog,
            onConfirm = {
                performAlarmSetupCommand(
                    alarmSetupViewModel?.onPermissionDialogConfirmed(),
                )
            },
            onDismiss = { alarmSetupViewModel?.onPermissionDialogDismissed() },
        )
    }
    LaunchedEffect(alarmController) {
        alarmController.ensureFullScreenAccess()
    }

    LaunchedEffect(alarmController, homeViewModel) {
        homeViewModel.observeAlarms(alarmController.savedAlarms)
    }

    LaunchedEffect(activeAlarmMission?.occurrenceId) {
        val occurrenceId = activeAlarmMission?.occurrenceId
        when {
            occurrenceId == null -> {
                handledActiveAlarmOccurrenceId = null
                if (navigationState.requestedScreen == AppScreen.ActiveAlarmTracking) {
                    navigationState.navigate(AppScreen.Home)
                }
            }

            handledActiveAlarmOccurrenceId != occurrenceId -> {
                handledActiveAlarmOccurrenceId = occurrenceId
                navigationState.navigate(AppScreen.ActiveAlarmTracking)
            }
        }
    }

    val pendingAppMessage = resolveAppMessage(
        screen = screen,
        authSessionState = authSessionState,
        homeErrorMessage = homeUiState.errorMessage,
        alarmSetupErrorMessage = alarmSetupUiState?.errorMessage,
        destinationErrorMessage = destinationUiState?.errorMessage,
    )
    AppMessageHost(
        state = pendingAppMessage?.state,
        onDismiss = {
            when (pendingAppMessage?.source) {
                AppMessageSource.Home -> homeViewModel.clearError()
                AppMessageSource.AlarmSetup -> alarmSetupViewModel?.clearError()
                AppMessageSource.Destination -> destinationViewModel?.clearError()
                null -> Unit
            }
        },
    )

    RingoutNavHost(
        navigationState = navigationState,
        screen = screen,
        viewModelStoreProvider = viewModelScopes.storeProvider,
        modifier = Modifier.fillMaxSize(),
        isBackBlocked = authNavigation?.isBackBlocked(screen, authSessionState) == true ||
            alarmEditorNavigation?.isBackBlocked(screen) == true,
        onBack = { route ->
            when (route) {
                AppRoute.AddAlarm,
                is AppRoute.EditAlarm,
                is AppRoute.Destination,
                AppRoute.AlarmSound,
                -> alarmEditorNavigation?.onBack(route, screen)
                AppRoute.Login, AppRoute.TermsAgreement ->
                    authNavigation?.onBack(route, screen, authSessionState)
                else -> navigationState.popBackStack(route)
            }
        },
        graph = {
            homeGraph(
                navigationState = navigationState,
                homeViewModel = homeViewModel,
                myPageViewModel = myPageViewModel,
                memberRepository = memberRepository,
                authSessionState = authSessionState,
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
                onLogin = { navigationState.navigate(AppRoute.Login) },
                onActiveAlarmMissionClick = {
                    navigationState.navigate(AppScreen.ActiveAlarmTracking)
                },
                onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
            )
            if (authNavigation != null) {
                authGraph(
                    authNavigation = authNavigation,
                    screen = screen,
                    authSessionState = authSessionState,
                )
            }
            if (alarmEditorNavigation != null) {
                alarmEditorGraph(
                    navigation = alarmEditorNavigation,
                    screen = screen,
                    authSessionState = authSessionState,
                    productAnalyticsRecorder = productAnalyticsRecorder,
                )
            }
        },
    ) {
        // 우선 표시되는 알람 울림과 진행 중인 미션 화면은 당분간 기존 화면 이동 방식을 유지한다.
        when (screen) {
            AppScreen.AlarmRinging -> ringingAlarm?.let { alarm ->
                AlarmRingingScreen(
                    alarmTime = alarm.alarmTime,
                    dateText = alarm.dateText,
                    limitMinutes = alarm.limitMinutes,
                    destinationName = alarm.destinationName,
                    onDismissAndNavigateClick = {
                        navigationState.navigate(AppScreen.ActiveAlarmTracking)
                        onRingingAlarmDismiss(alarm.id)
                    },
                )
            }

            AppScreen.ActiveAlarmTracking -> activeAlarmMission?.let { mission ->
                ActiveAlarmTrackingScreen(
                    mission = mission,
                    currentLocation = activeAlarmMissionLocation,
                    locationState = missionLocationState,
                    onBackClick = { navigationState.navigate(AppScreen.Home) },
                    onForceEndClick = onActiveAlarmMissionForceEnd,
                    onForceEndHoldStarted = onActiveAlarmMissionForceEndHoldStarted,
                    onForceEndHoldCancelled = onActiveAlarmMissionForceEndHoldCancelled,
                    onForceEndHoldCompleted = onActiveAlarmMissionForceEndHoldCompleted,
                    onExpired = onActiveAlarmMissionExpired,
                )
            }

            AppScreen.Home,
            AppScreen.MyPage,
            AppScreen.Settings,
            AppScreen.NicknameChange,
            AppScreen.Login,
            AppScreen.TermsAgreement,
            AppScreen.AddAlarm,
            AppScreen.EditAlarm,
            AppScreen.Destination,
            AppScreen.AlarmSound,
            -> Unit // HomeGraph, AuthGraph 또는 AlarmEditorGraph에서 표시한다.
        }
    }
}

internal enum class AppScreen {
    AlarmRinging,
    Home,
    AddAlarm,
    EditAlarm,
    Destination,
    AlarmSound,
    MyPage,
    NicknameChange,
    Login,
    TermsAgreement,
    Settings,
    ActiveAlarmTracking,
}

internal fun AuthSessionState.toAnalyticsLoginStateOrNull(): AnalyticsLoginState? = when (this) {
    AuthSessionState.Restoring -> null
    AuthSessionState.Unauthenticated -> AnalyticsLoginState.LoggedOut
    AuthSessionState.ReauthenticationRequired -> AnalyticsLoginState.LoggedOut
    AuthSessionState.Authenticated -> AnalyticsLoginState.LoggedIn
}

internal fun resolveAppScreen(
    requestedScreen: AppScreen,
    hasRingingAlarm: Boolean,
    hasActiveAlarmMission: Boolean,
    authSessionState: AuthSessionState,
): AppScreen = when {
    hasRingingAlarm -> AppScreen.AlarmRinging
    authSessionState == AuthSessionState.ReauthenticationRequired -> AppScreen.Login
    requestedScreen == AppScreen.ActiveAlarmTracking && !hasActiveAlarmMission -> AppScreen.Home
    else -> requestedScreen
}

internal fun canShowAppDialog(
    screen: AppScreen,
    authSessionState: AuthSessionState,
): Boolean =
    screen != AppScreen.AlarmRinging &&
        authSessionState != AuthSessionState.ReauthenticationRequired
