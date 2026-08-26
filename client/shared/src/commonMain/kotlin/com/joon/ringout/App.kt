package com.joon.ringout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.DefaultMissionLocationState
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.alarm.rememberAlarmController
import com.joon.ringout.di.AppContainer
import com.joon.ringout.presentation.activemission.ActiveAlarmTrackingScreen
import com.joon.ringout.presentation.activemission.components.MissionLocationPermissionDialog
import com.joon.ringout.presentation.alarmsound.AlarmSoundScreen
import com.joon.ringout.presentation.alarmsetup.AlarmSetupScreen
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.destination.DefaultDestinationSelection
import com.joon.ringout.presentation.destination.DestinationMapScreen
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.destination.belongsToDestinationRequest
import com.joon.ringout.presentation.destination.toDestinationSelection
import com.joon.ringout.presentation.home.HomeScreen
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.login.LoginScreen
import com.joon.ringout.presentation.login.LoginViewModel
import com.joon.ringout.presentation.appbootstrap.AppBootstrapViewModel
import com.joon.ringout.presentation.common.AppMessageSource
import com.joon.ringout.presentation.common.resolveAppMessage
import com.joon.ringout.presentation.common.component.AppMessageHost
import com.joon.ringout.presentation.onboarding.OnboardingScreen
import com.joon.ringout.presentation.ringing.AlarmRingingScreen
import com.joon.ringout.presentation.ringing.AlarmRingingUiState
import com.joon.ringout.presentation.signup.SignupViewModel
import com.joon.ringout.presentation.termsagreement.TermId
import com.joon.ringout.presentation.termsagreement.TermsAgreementScreen
import com.joon.ringout.presentation.mypage.DefaultMyPagePolicies
import com.joon.ringout.presentation.mypage.MyPageScreen
import com.joon.ringout.presentation.mypage.MyPageViewModel
import com.joon.ringout.presentation.mypage.PolicyId
import com.joon.ringout.presentation.mypage.currentMissionYearMonth
import com.joon.ringout.presentation.mypage.findPolicyUrl
import com.joon.ringout.presentation.mypage.model.MyPageAccountAction
import com.joon.ringout.presentation.mypage.model.MyPageAccountActionState
import com.joon.ringout.presentation.mypage.model.MyPageAccountStatus
import com.joon.ringout.presentation.nickname.NicknameChangeScreen
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
                OnboardingScreen(
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
    val uriHandler = LocalUriHandler.current
    val productAnalyticsRecorder = appContainer.productAnalyticsRecorder
    val destinationRepository = appContainer.destinationRepository
    val destinationViewModel: DestinationViewModel = viewModel {
        DestinationViewModel(
            repository = destinationRepository,
            productAnalyticsRecorder = productAnalyticsRecorder,
        )
    }
    val destinationUiState = destinationViewModel.uiState
    val authSession = appContainer.authSession
    val authRepository = appContainer.authRepository
    val memberRepository = appContainer.memberRepository
    val authSessionState by authSession.state.collectAsState()
    val analyticsLoginState = authSessionState.toAnalyticsLoginStateOrNull()
    val myPageViewModel: MyPageViewModel = viewModel {
        MyPageViewModel(
            getMissionSuccessDates = GetMissionSuccessDates(
                appContainer.missionHistoryRepository,
            ),
            memberRepository = memberRepository,
            authRepository = authRepository,
            productAnalyticsRecorder = productAnalyticsRecorder,
            initialMonth = currentMissionYearMonth(),
        )
    }
    val myPageUiState = myPageViewModel.uiState
    LaunchedEffect(authSessionState) {
        when (authSessionState) {
            AuthSessionState.Restoring -> myPageViewModel.onSessionRestoring()
            AuthSessionState.Unauthenticated,
            AuthSessionState.ReauthenticationRequired,
            -> {
                myPageViewModel.onLoggedOut()
                destinationViewModel.onLoggedOut()
            }

            AuthSessionState.Authenticated -> myPageViewModel.onAuthenticated()
        }
    }
    val loginViewModel: LoginViewModel = viewModel {
        LoginViewModel(
            authRepository = authRepository,
            productAnalyticsRecorder = productAnalyticsRecorder,
        )
    }
    val signupViewModel: SignupViewModel = viewModel {
        SignupViewModel(
            authRepository = authRepository,
            destinationRepository = destinationRepository,
            productAnalyticsRecorder = productAnalyticsRecorder,
        )
    }
    val signupUiState = signupViewModel.uiState
    val alarmSetupViewModel: AlarmSetupViewModel = viewModel { AlarmSetupViewModel() }
    val alarmSetupUiState = alarmSetupViewModel.uiState
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel() }
    val homeUiState = homeViewModel.uiState
    var screenName by rememberSaveable { mutableStateOf(AppScreen.Home.name) }
    var handledActiveAlarmOccurrenceId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var destinationRequestId by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(authRepository) {
        authRepository.restoreSession()
    }
    LaunchedEffect(
        authSessionState,
        alarmSetupUiState,
        alarmSetupViewModel.permissionDialog,
        destinationUiState.errorMessage,
        homeUiState.errorMessage,
    ) {
        if (authSessionState == AuthSessionState.ReauthenticationRequired) {
            signupViewModel.resetSignup()
            alarmSetupViewModel.resetSaveFlow()
            homeViewModel.clearError()
            myPageViewModel.resetAccountActionFlow()
            if (destinationUiState.errorMessage != null) {
                destinationViewModel.clearError()
            }
            screenName = AppScreen.Login.name
        }
    }
    val completedAccountAction =
        myPageUiState.accountAction as? MyPageAccountActionState.Completed
    LaunchedEffect(completedAccountAction?.eventId) {
        val completed = completedAccountAction ?: return@LaunchedEffect
        signupViewModel.resetSignup()
        screenName = when (completed.action) {
            MyPageAccountAction.Logout -> AppScreen.Login.name
            MyPageAccountAction.Withdraw -> AppScreen.MyPage.name
        }
        myPageViewModel.consumeAccountActionCompletedEvent(completed.eventId)
    }
    val requestedScreen = AppScreen.valueOf(screenName)
    val screen = resolveAppScreen(
        requestedScreen = requestedScreen,
        hasRingingAlarm = ringingAlarm != null,
        hasActiveAlarmMission = activeAlarmMission != null,
        authSessionState = authSessionState,
    )
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
            if (alarmSetupViewModel.onSaveCompleted(request)) {
                screenName = AppScreen.Home.name
            }
        },
        onSaveError = { request, message ->
            alarmSetupViewModel.onSaveError(request, message)
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
    val performHomeCommand: (HomeViewModel.Command) -> Unit = { command ->
        when (command) {
            is HomeViewModel.Command.SetAlarmEnabled ->
                alarmController.setEnabled(command.alarmId, command.enabled)

            is HomeViewModel.Command.DeleteAlarm ->
                alarmController.deleteAlarm(command.alarmId)
        }
    }

    LaunchedEffect(
        alarmSetupUiState.pendingSaveRequest?.id,
        alarmSetupUiState.isScheduling,
        missionLocationState.revision,
        screen,
    ) {
        performAlarmSetupCommand(
            alarmSetupViewModel.onLocationStateChanged(
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
            decision = alarmSetupViewModel.permissionDialog,
            onConfirm = {
                performAlarmSetupCommand(
                    alarmSetupViewModel.onPermissionDialogConfirmed(),
                )
            },
            onDismiss = alarmSetupViewModel::onPermissionDialogDismissed,
        )
    }
    val alarmSetupScreen = if (alarmSetupUiState.isEditing) {
        AppScreen.EditAlarm
    } else {
        AppScreen.AddAlarm
    }

    LaunchedEffect(alarmController) {
        alarmController.ensureFullScreenAccess()
    }

    LaunchedEffect(alarmController) {
        homeViewModel.observeAlarms(alarmController.savedAlarms)
    }

    LaunchedEffect(destinationUiState.savedEvent?.eventId) {
        destinationUiState.savedEvent?.let { event ->
            if (
                event.belongsToDestinationRequest(
                    currentRequestId = destinationRequestId,
                    isDestinationScreenVisible = screenName == AppScreen.Destination.name,
                )
            ) {
                alarmSetupViewModel.updateDestination(
                    event.destination.toDestinationSelection(),
                )
                screenName = alarmSetupScreen.name
            }
            destinationViewModel.consumeSavedEvent(event.eventId)
        }
    }

    LaunchedEffect(activeAlarmMission?.occurrenceId) {
        val occurrenceId = activeAlarmMission?.occurrenceId
        when {
            occurrenceId == null -> {
                handledActiveAlarmOccurrenceId = null
                if (screenName == AppScreen.ActiveAlarmTracking.name) {
                    screenName = AppScreen.Home.name
                }
            }

            handledActiveAlarmOccurrenceId != occurrenceId -> {
                handledActiveAlarmOccurrenceId = occurrenceId
                screenName = AppScreen.ActiveAlarmTracking.name
            }
        }
    }

    val pendingAppMessage = resolveAppMessage(
        screen = screen,
        authSessionState = authSessionState,
        homeErrorMessage = homeUiState.errorMessage,
        alarmSetupErrorMessage = alarmSetupUiState.errorMessage,
        destinationErrorMessage = destinationUiState.errorMessage,
    )
    AppMessageHost(
        state = pendingAppMessage?.state,
        onDismiss = {
            when (pendingAppMessage?.source) {
                AppMessageSource.Home -> homeViewModel.clearError()
                AppMessageSource.AlarmSetup -> alarmSetupViewModel.clearError()
                AppMessageSource.Destination -> destinationViewModel.clearError()
                null -> Unit
            }
        },
    )

    when (screen) {
        AppScreen.AlarmRinging -> ringingAlarm?.let { alarm ->
            AlarmRingingScreen(
                alarmTime = alarm.alarmTime,
                dateText = alarm.dateText,
                limitMinutes = alarm.limitMinutes,
                destinationName = alarm.destinationName,
                onDismissAndNavigateClick = {
                    screenName = AppScreen.ActiveAlarmTracking.name
                    onRingingAlarmDismiss(alarm.id)
                },
            )
        }

        AppScreen.Home -> HomeScreen(
            uiState = homeUiState,
            activeAlarmMission = activeAlarmMission,
            onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
            onAddAlarm = {
                alarmSetupViewModel.startCreating(
                    initialTime = currentLocalClockSnapshot().to24HourTimeString(),
                )
                screenName = AppScreen.AddAlarm.name
            },
            onAlarmClick = { alarmId ->
                val request = homeViewModel.alarmScheduleRequest(alarmId)
                if (request == null) {
                    homeViewModel.showError(
                        "알람의 목적지 정보를 불러오지 못했습니다.",
                    )
                } else {
                    alarmSetupViewModel.startEditing(request)
                    screenName = AppScreen.EditAlarm.name
                }
            },
            onAlarmEnabledChange = { alarmId, enabled ->
                performHomeCommand(
                    homeViewModel.onAlarmEnabledChange(alarmId, enabled),
                )
            },
            onAlarmDelete = { alarmId ->
                performHomeCommand(homeViewModel.onAlarmDelete(alarmId))
            },
            onActiveAlarmMissionClick = {
                screenName = AppScreen.ActiveAlarmTracking.name
            },
            onSettingsClick = { screenName = AppScreen.MyPage.name },
        )

        AppScreen.ActiveAlarmTracking -> activeAlarmMission?.let { mission ->
            ActiveAlarmTrackingScreen(
                mission = mission,
                currentLocation = activeAlarmMissionLocation,
                locationState = missionLocationState,
                onBackClick = { screenName = AppScreen.Home.name },
                onForceEndClick = onActiveAlarmMissionForceEnd,
                onForceEndHoldStarted = onActiveAlarmMissionForceEndHoldStarted,
                onForceEndHoldCancelled = onActiveAlarmMissionForceEndHoldCancelled,
                onForceEndHoldCompleted = onActiveAlarmMissionForceEndHoldCompleted,
                onExpired = onActiveAlarmMissionExpired,
            )
        }

        AppScreen.MyPage,
        AppScreen.Settings,
        -> MyPageScreen(
            uiState = myPageUiState,
            themeMode = themeMode,
            appVersion = appVersion,
            policies = DefaultMyPagePolicies,
            onScreenEntered = myPageViewModel::onScreenEntered,
            onThemeModeChange = onThemeModeChange,
            onPreviousMonthClick = myPageViewModel::onPreviousMonthClick,
            onNextMonthClick = myPageViewModel::onNextMonthClick,
            onCalendarRetry = myPageViewModel::retryCalendar,
            onBackClick = { screenName = AppScreen.Home.name },
            onAccountStatusClick = { screenName = AppScreen.Login.name },
            onAccountRetry = myPageViewModel::retryAccount,
            onEditProfileClick = {
                if (myPageUiState.accountStatus is MyPageAccountStatus.LoggedIn) {
                    screenName = AppScreen.NicknameChange.name
                }
            },
            onLogoutConfirm = myPageViewModel::logout,
            onWithdrawConfirm = myPageViewModel::withdraw,
            onAccountActionErrorDismiss = myPageViewModel::clearAccountActionError,
            onPolicyClick = { policyId ->
                findPolicyUrl(policyId)?.let { url ->
                    runCatching { uriHandler.openUri(url) }
                }
            },
        )

        AppScreen.NicknameChange -> {
            val account = myPageUiState.accountStatus as? MyPageAccountStatus.LoggedIn
            if (account == null) {
                LaunchedEffect(authSessionState) {
                    if (authSessionState != AuthSessionState.Restoring) {
                        screenName = AppScreen.MyPage.name
                    }
                }
            } else {
                NicknameChangeScreen(
                    initialNickname = account.nickname,
                    memberRepository = memberRepository,
                    onBackClick = { screenName = AppScreen.MyPage.name },
                    onConfirmClick = { updatedNickname ->
                        myPageViewModel.onNicknameUpdated(updatedNickname)
                        screenName = AppScreen.MyPage.name
                    },
                )
            }
        }

        AppScreen.Login -> LoginScreen(
            onBackClick = { screenName = AppScreen.MyPage.name },
            onAuthenticated = {
                signupViewModel.resetSignup()
                screenName = AppScreen.Home.name
            },
            onSignupRequired = { signupToken, provider ->
                signupViewModel.startSignup(
                    signupToken = signupToken,
                    provider = provider,
                )
                screenName = AppScreen.TermsAgreement.name
            },
            viewModel = loginViewModel,
        )

        AppScreen.TermsAgreement -> {
            if (!signupUiState.hasPendingSignup) {
                LaunchedEffect(Unit) {
                    screenName = AppScreen.Login.name
                }
            } else {
                val completedEventId = signupUiState.completedEventId
                LaunchedEffect(completedEventId) {
                    completedEventId ?: return@LaunchedEffect
                    screenName = AppScreen.Home.name
                    signupViewModel.consumeCompletedEvent(completedEventId)
                }
                TermsAgreementScreen(
                    onStart = signupViewModel::signup,
                    onTermDetailClick = { termId ->
                        val policyId = when (termId) {
                            TermId.Service -> PolicyId("terms")
                            TermId.Privacy -> PolicyId("privacy")
                            else -> null
                        }
                        val policyUrl = policyId?.let(::findPolicyUrl)
                        policyUrl?.let { url -> runCatching { uriHandler.openUri(url) } }
                    },
                    isSaving = signupUiState.isSaving,
                    errorMessage = signupUiState.errorMessage,
                )
            }
        }

        AppScreen.AddAlarm,
        AppScreen.EditAlarm,
        AppScreen.Destination,
        AppScreen.AlarmSound,
        -> Box(Modifier.fillMaxSize()) {
            AlarmSetupScreen(
                uiState = alarmSetupUiState,
                onAmPmChange = alarmSetupViewModel::updateAmPm,
                onHourChange = alarmSetupViewModel::updateHour,
                onMinuteChange = alarmSetupViewModel::updateMinute,
                onDayClick = alarmSetupViewModel::toggleDay,
                onLimitMinutesChange = alarmSetupViewModel::updateLimitMinutes,
                onBackClick = {
                    if (!alarmSetupUiState.isSaveInProgress) {
                        screenName = AppScreen.Home.name
                    }
                },
                onDestinationClick = {
                    destinationRequestId += 1L
                    screenName = AppScreen.Destination.name
                },
                onAlarmSoundClick = { screenName = AppScreen.AlarmSound.name },
                onSaveClick = { alarmSetupViewModel.requestSave() },
            )

            if (screen == AppScreen.Destination) {
                DestinationMapScreen(
                    initialSelection = alarmSetupUiState.destination
                        ?: DefaultDestinationSelection,
                    requestCurrentLocationOnStart = false,
                    isAuthenticated = authSessionState == AuthSessionState.Authenticated,
                    onEntered = destinationViewModel::onScreenEntered,
                    onBackClick = { screenName = alarmSetupScreen.name },
                    onConfirmClick = saveDestination@ { destination ->
                        val loginState = analyticsLoginState ?: return@saveDestination
                        destinationViewModel.save(
                            destination = destination,
                            requestId = destinationRequestId,
                            loginState = loginState,
                        )
                    },
                    onSavedDestinationConfirmClick = { savedDestination ->
                        alarmSetupViewModel.updateDestination(
                            savedDestination.toDestinationSelection(),
                        )
                        screenName = alarmSetupScreen.name
                    },
                    savedDestinations = destinationUiState.destinations,
                    onSavedDestinationRename = destinationViewModel::rename,
                    onSavedDestinationDeleteClick = destinationViewModel::delete,
                    onSavedDestinationSelected = { source ->
                        analyticsLoginState?.let { loginState ->
                            productAnalyticsRecorder.recordDestinationSelected(
                                source = source,
                                loginState = loginState,
                            )
                        }
                    },
                    isSaveInProgress = destinationUiState.isSaving,
                    isDestinationActionEnabled = analyticsLoginState != null,
                )
            }

            if (screen == AppScreen.AlarmSound) {
                AlarmSoundScreen(
                    selectedSound = alarmSetupUiState.alarmSound,
                    onBackClick = { screenName = alarmSetupScreen.name },
                    onSaveClick = { selectedSound ->
                        alarmSetupViewModel.updateAlarmSound(selectedSound)
                        screenName = alarmSetupScreen.name
                    },
                )
            }
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
