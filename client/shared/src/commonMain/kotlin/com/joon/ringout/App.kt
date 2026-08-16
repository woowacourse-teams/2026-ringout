package com.joon.ringout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.data.auth.rememberAuthRepository
import com.joon.ringout.data.preferences.DataStoreAppPreferencesRepository
import com.joon.ringout.data.preferences.rememberAppPreferencesDataStore
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.DefaultMissionLocationState
import com.joon.ringout.alarm.MissionLocationPermissionDecision
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.alarm.newAlarmId
import com.joon.ringout.alarm.permissionDecision
import com.joon.ringout.alarm.rememberAlarmController
import com.joon.ringout.presentation.activemission.ActiveAlarmTrackingScreen
import com.joon.ringout.presentation.activemission.components.MissionLocationPermissionDialog
import com.joon.ringout.presentation.alarmsound.AlarmSoundScreen
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import com.joon.ringout.presentation.alarmsetup.AlarmSetupScreen
import com.joon.ringout.presentation.alarmsetup.PlatformDefaultAlarmSoundName
import com.joon.ringout.presentation.alarmsetup.components.weekdaySummary
import com.joon.ringout.presentation.destination.DefaultDestinationSelection
import com.joon.ringout.presentation.destination.DestinationMapScreen
import com.joon.ringout.presentation.destination.DestinationSelection
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.destination.belongsToDestinationRequest
import com.joon.ringout.presentation.destination.isConfiguredDestination
import com.joon.ringout.presentation.destination.rememberDestinationRepository
import com.joon.ringout.presentation.home.HomeAlarm
import com.joon.ringout.presentation.home.HomeScreen
import com.joon.ringout.presentation.login.LoginScreen
import com.joon.ringout.presentation.login.LoginViewModel
import com.joon.ringout.presentation.appbootstrap.AppBootstrapViewModel
import com.joon.ringout.presentation.onboarding.OnboardingScreen
import com.joon.ringout.presentation.ringing.AlarmRingingScreen
import com.joon.ringout.presentation.ringing.AlarmRingingUiState
import com.joon.ringout.presentation.settings.SettingsScreen
import com.joon.ringout.presentation.termsagreement.SignupViewModel
import com.joon.ringout.presentation.termsagreement.TermId
import com.joon.ringout.presentation.termsagreement.TermsAgreementScreen
import com.joon.ringout.presentation.mypage.DefaultMyPagePolicies
import com.joon.ringout.presentation.mypage.MyPageScreen
import com.joon.ringout.presentation.mypage.PolicyId
import com.joon.ringout.presentation.mypage.findPolicyUrl
import com.joon.ringout.presentation.currentLocalClockSnapshot
import com.joon.ringout.presentation.to24HourTimeString
import kotlinx.coroutines.flow.collect

@Composable
fun App(
    appVersion: String = "",
    googleServerClientId: String = "",
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
    val preferencesDataStore = rememberAppPreferencesDataStore()
    val appPreferencesRepository = remember(preferencesDataStore) {
        DataStoreAppPreferencesRepository(preferencesDataStore)
    }
    val appBootstrapViewModel = viewModel {
        AppBootstrapViewModel(appPreferencesRepository)
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
                    themeMode = appBootstrapUiState.themeMode,
                    appVersion = appVersion,
                    googleServerClientId = googleServerClientId,
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
    themeMode: ThemeMode,
    appVersion: String,
    googleServerClientId: String,
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
    val destinationRepository = rememberDestinationRepository()
    val destinationViewModel: DestinationViewModel = viewModel {
        DestinationViewModel(destinationRepository)
    }
    val destinationUiState = destinationViewModel.uiState
    val authSession = remember { AuthSession() }
    val authRepository = rememberAuthRepository(authSession)
    val loginViewModel: LoginViewModel = viewModel {
        LoginViewModel(authRepository)
    }
    val signupViewModel: SignupViewModel = viewModel {
        SignupViewModel(authRepository)
    }
    var destinationName by rememberSaveable { mutableStateOf("") }
    var destinationAddress by rememberSaveable { mutableStateOf("") }
    var destinationLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var destinationLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var alarmSoundName by rememberSaveable {
        mutableStateOf(PlatformDefaultAlarmSoundName)
    }
    var alarmSoundUri by rememberSaveable { mutableStateOf<String?>(null) }
    var screenName by rememberSaveable { mutableStateOf(AppScreen.Home.name) }
    var pendingSignupToken by remember { mutableStateOf<String?>(null) }
    var handledActiveAlarmOccurrenceId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var editingAlarmId by rememberSaveable { mutableStateOf<String?>(null) }
    var newAlarmInitialTime by rememberSaveable {
        mutableStateOf(UnavailableEditingAlarmTime)
    }
    var destinationRequestId by rememberSaveable { mutableStateOf(0L) }
    var alarms by remember { mutableStateOf<List<HomeAlarm>?>(null) }
    var alarmScheduleError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAlarmRequest by remember { mutableStateOf<AlarmScheduleRequest?>(null) }
    var isAlarmSaveInProgress by remember { mutableStateOf(false) }
    var locationPermissionDialog by remember {
        mutableStateOf<MissionLocationPermissionDecision?>(null)
    }
    var didRequestFullAccuracy by rememberSaveable { mutableStateOf(false) }
    val destination = destinationLatitude?.let { latitude ->
        destinationLongitude?.let { longitude ->
            DestinationSelection(
                name = destinationName,
                address = destinationAddress,
                latitude = latitude,
                longitude = longitude,
            ).takeIf(DestinationSelection::isConfiguredDestination)
        }
    }
    val alarmSound = AlarmSoundSelection(
        name = if (alarmSoundUri == null) {
            PlatformDefaultAlarmSoundName
        } else {
            alarmSoundName.ifBlank { PlatformDefaultAlarmSoundName }
        },
        uri = alarmSoundUri,
    )
    val requestedScreen = AppScreen.valueOf(screenName)
    val screen = when {
        ringingAlarm != null -> AppScreen.AlarmRinging
        requestedScreen == AppScreen.ActiveAlarmTracking && activeAlarmMission == null ->
            AppScreen.Home
        else -> requestedScreen
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
            if (pendingAlarmRequest?.id == request.id) {
                pendingAlarmRequest = null
                isAlarmSaveInProgress = false
                editingAlarmId = null
                screenName = AppScreen.Home.name
            }
        },
        onSaveError = { request, message ->
            if (pendingAlarmRequest?.id == request.id) {
                pendingAlarmRequest = null
                isAlarmSaveInProgress = false
                didRequestFullAccuracy = false
                alarmScheduleError = message
            }
        },
        onError = {
            alarmScheduleError = it
        },
    )

    LaunchedEffect(
        pendingAlarmRequest,
        missionLocationState.revision,
        screen,
    ) {
        if (screen == AppScreen.AlarmRinging) return@LaunchedEffect
        val request = pendingAlarmRequest ?: return@LaunchedEffect
        if (isAlarmSaveInProgress) return@LaunchedEffect
        when (
            val decision = missionLocationState.permissionDecision(
                didRequestFullAccuracy = didRequestFullAccuracy,
            )
        ) {
            MissionLocationPermissionDecision.READY -> {
                locationPermissionDialog = null
                didRequestFullAccuracy = false
                if (!isAlarmSaveInProgress) {
                    isAlarmSaveInProgress = true
                    alarmController.schedule(request)
                }
            }

            MissionLocationPermissionDecision.EXPLAIN_WHEN_IN_USE ->
                if (useSystemLocationPermissionUiOnly) {
                    locationPermissionDialog = null
                    onRequestWhenInUseLocation()
                } else {
                    locationPermissionDialog = decision
                }

            MissionLocationPermissionDecision.EXPLAIN_ALWAYS ->
                if (useSystemLocationPermissionUiOnly) {
                    locationPermissionDialog = null
                    onRequestAlwaysLocation()
                } else {
                    locationPermissionDialog = decision
                }

            MissionLocationPermissionDecision.CONFIRM_ALWAYS_RESULT -> {
                if (!useSystemLocationPermissionUiOnly) {
                    locationPermissionDialog = decision
                }
            }

            MissionLocationPermissionDecision.WARN_REDUCED_ACCURACY ->
                if (useSystemLocationPermissionUiOnly) {
                    locationPermissionDialog = null
                    didRequestFullAccuracy = true
                    onRequestTemporaryFullAccuracy()
                } else {
                    locationPermissionDialog = decision
                }

            MissionLocationPermissionDecision.SERVICES_DISABLED -> {
                pendingAlarmRequest = null
                isAlarmSaveInProgress = false
                didRequestFullAccuracy = false
                alarmScheduleError = "위치 서비스가 꺼져 있어 목적지 알람을 시작할 수 없습니다."
            }

            MissionLocationPermissionDecision.DENIED -> {
                pendingAlarmRequest = null
                isAlarmSaveInProgress = false
                didRequestFullAccuracy = false
                alarmScheduleError = "위치 권한이 없어 목적지 알람을 시작할 수 없습니다."
            }

            MissionLocationPermissionDecision.ALWAYS_REQUEST_FAILED -> {
                pendingAlarmRequest = null
                isAlarmSaveInProgress = false
                didRequestFullAccuracy = false
                alarmScheduleError = "위치 권한 상태를 확인하지 못했습니다. 다시 시도해 주세요."
                onConfirmAlwaysLocationResult()
            }

            MissionLocationPermissionDecision.RESTRICTED -> {
                pendingAlarmRequest = null
                isAlarmSaveInProgress = false
                didRequestFullAccuracy = false
                alarmScheduleError = "이 기기에서는 위치 사용이 제한되어 있습니다."
            }
        }
    }

    if (!useSystemLocationPermissionUiOnly && screen != AppScreen.AlarmRinging) {
        MissionLocationPermissionDialog(
            decision = locationPermissionDialog,
            onConfirm = {
                when (locationPermissionDialog) {
                    MissionLocationPermissionDecision.EXPLAIN_WHEN_IN_USE ->
                        onRequestWhenInUseLocation()

                    MissionLocationPermissionDecision.EXPLAIN_ALWAYS ->
                        onRequestAlwaysLocation()

                    MissionLocationPermissionDecision.CONFIRM_ALWAYS_RESULT ->
                        onConfirmAlwaysLocationResult()

                    MissionLocationPermissionDecision.WARN_REDUCED_ACCURACY -> {
                        didRequestFullAccuracy = true
                        onRequestTemporaryFullAccuracy()
                    }

                    else -> Unit
                }
                locationPermissionDialog = null
            },
            onDismiss = {
                locationPermissionDialog = null
                pendingAlarmRequest = null
                isAlarmSaveInProgress = false
                didRequestFullAccuracy = false
            },
        )
    }
    val visibleAlarms = alarms.orEmpty()
    val editingAlarm = editingAlarmId?.let { alarmId ->
        visibleAlarms.firstOrNull { it.id == alarmId }
    }
    val alarmSetupScreen = if (editingAlarmId == null) {
        AppScreen.AddAlarm
    } else {
        AppScreen.EditAlarm
    }
    val initialSelectedDays = when {
        editingAlarm == null -> DefaultSelectedDays
        editingAlarm.repeatEnabled -> editingAlarm.selectedDays
        else -> emptyList()
    }

    LaunchedEffect(alarmController) {
        alarmController.ensureFullScreenAccess()
    }

    LaunchedEffect(alarmController) {
        alarmController.savedAlarms.collect { savedAlarms ->
            alarms = savedAlarms.map { saved ->
                saved.request.toHomeAlarm(enabled = saved.enabled)
            }
        }
    }

    LaunchedEffect(destinationUiState.savedEvent?.eventId) {
        destinationUiState.savedEvent?.let { event ->
            if (
                event.belongsToDestinationRequest(
                    currentRequestId = destinationRequestId,
                    isDestinationScreenVisible = screenName == AppScreen.Destination.name,
                )
            ) {
                destinationName = event.destination.name
                destinationAddress = event.destination.address
                destinationLatitude = event.destination.latitude
                destinationLongitude = event.destination.longitude
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
                editingAlarmId = null
                screenName = AppScreen.ActiveAlarmTracking.name
            }
        }
    }

    if (screen != AppScreen.AlarmRinging && alarmScheduleError != null) {
        AlertDialog(
            onDismissRequest = { alarmScheduleError = null },
            title = { Text("알람을 처리할 수 없습니다") },
            text = { Text(alarmScheduleError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { alarmScheduleError = null }) {
                    Text("확인")
                }
            },
        )
    }

    if (
        screen != AppScreen.AlarmRinging &&
        alarmScheduleError == null &&
        destinationUiState.errorMessage != null
    ) {
        AlertDialog(
            onDismissRequest = destinationViewModel::clearError,
            title = { Text("목적지를 처리할 수 없습니다") },
            text = { Text(destinationUiState.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = destinationViewModel::clearError) {
                    Text("확인")
                }
            },
        )
    }

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
            alarms = visibleAlarms,
            activeAlarmMission = activeAlarmMission,
            onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
            onAddAlarm = {
                editingAlarmId = null
                newAlarmInitialTime = currentLocalClockSnapshot().to24HourTimeString()
                destinationName = ""
                destinationAddress = ""
                destinationLatitude = null
                destinationLongitude = null
                alarmSoundName = PlatformDefaultAlarmSoundName
                alarmSoundUri = null
                screenName = AppScreen.AddAlarm.name
            },
            onAlarmClick = { alarmId ->
                visibleAlarms.firstOrNull { it.id == alarmId }?.let { alarm ->
                    editingAlarmId = alarm.id
                    destinationName = alarm.destination
                    destinationAddress = alarm.targetAddress
                    destinationLatitude = alarm.targetLatitude
                    destinationLongitude = alarm.targetLongitude
                    alarmSoundName = if (alarm.alarmSoundUri == null) {
                        PlatformDefaultAlarmSoundName
                    } else {
                        alarm.alarmSoundName.ifBlank { PlatformDefaultAlarmSoundName }
                    }
                    alarmSoundUri = alarm.alarmSoundUri
                    screenName = AppScreen.EditAlarm.name
                }
            },
            onAlarmEnabledChange = { alarmId, enabled ->
                alarmController.setEnabled(alarmId, enabled)
            },
            onAlarmDelete = { alarmId ->
                alarmController.deleteAlarm(alarmId)
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
            themeMode = themeMode,
            appVersion = appVersion,
            policies = DefaultMyPagePolicies,
            onThemeModeChange = onThemeModeChange,
            onBackClick = { screenName = AppScreen.Home.name },
            onAccountStatusClick = { screenName = AppScreen.Login.name },
            onPolicyClick = { policyId ->
                findPolicyUrl(policyId)?.let { url ->
                    runCatching { uriHandler.openUri(url) }
                }
            },
        )

        AppScreen.Login -> LoginScreen(
            onBackClick = { screenName = AppScreen.MyPage.name },
            googleServerClientId = googleServerClientId,
            onAuthenticated = { screenName = AppScreen.Home.name },
            onSignupRequired = { signupToken ->
                pendingSignupToken = signupToken
                screenName = AppScreen.TermsAgreement.name
            },
            viewModel = loginViewModel,
        )

        AppScreen.TermsAgreement -> {
            val signupToken = pendingSignupToken
            if (signupToken == null) {
                LaunchedEffect(Unit) {
                    screenName = AppScreen.Login.name
                }
            } else {
                val signupUiState = signupViewModel.uiState
                val completedEventId = signupUiState.completedEventId
                LaunchedEffect(completedEventId) {
                    completedEventId ?: return@LaunchedEffect
                    pendingSignupToken = null
                    signupViewModel.consumeCompletedEvent(completedEventId)
                    screenName = AppScreen.Home.name
                }
                TermsAgreementScreen(
                    onStart = { agreedTerms ->
                        signupViewModel.signup(signupToken, agreedTerms)
                    },
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
                destination = destination?.name.orEmpty(),
                alarmSound = alarmSound,
                isDestinationSet = destination != null,
                initialTime = alarmSetupInitialTime(
                    editingAlarmId = editingAlarmId,
                    editingAlarmTime = editingAlarm?.time,
                    newAlarmInitialTime = newAlarmInitialTime,
                ),
                initialSelectedDays = initialSelectedDays,
                initialLimitMinutes = editingAlarm?.timeLimitMinutes ?: DefaultLimitMinutes,
                isSaveInProgress = pendingAlarmRequest != null || isAlarmSaveInProgress,
                onBackClick = {
                    if (pendingAlarmRequest == null && !isAlarmSaveInProgress) {
                        didRequestFullAccuracy = false
                        editingAlarmId = null
                        screenName = AppScreen.Home.name
                    }
                },
                onDestinationClick = {
                    destinationRequestId += 1L
                    screenName = AppScreen.Destination.name
                },
                onAlarmSoundClick = { screenName = AppScreen.AlarmSound.name },
                onSaveClick = saveAlarm@ { time, selectedDays, repeatEnabled, limitMinutes, alarmSound ->
                    if (pendingAlarmRequest != null || isAlarmSaveInProgress) return@saveAlarm
                    val configuredDestination = destination ?: return@saveAlarm
                    didRequestFullAccuracy = false
                    pendingAlarmRequest = AlarmScheduleRequest(
                        id = editingAlarmId ?: newAlarmId(),
                        time = time,
                        selectedDays = selectedDays,
                        repeatEnabled = repeatEnabled,
                        limitMinutes = limitMinutes,
                        destinationName = configuredDestination.name,
                        destinationAddress = configuredDestination.address,
                        destinationLatitude = configuredDestination.latitude,
                        destinationLongitude = configuredDestination.longitude,
                        alarmSoundName = alarmSound.name,
                        alarmSoundUri = alarmSound.uri,
                    )
                },
            )

            if (screen == AppScreen.Destination) {
                DestinationMapScreen(
                    initialSelection = destination ?: DefaultDestinationSelection,
                    requestCurrentLocationOnStart = false,
                    onBackClick = { screenName = alarmSetupScreen.name },
                    onConfirmClick = { destination ->
                        destinationViewModel.save(
                            destination = destination,
                            requestId = destinationRequestId,
                        )
                    },
                    onSavedDestinationConfirmClick = { savedDestination ->
                        destinationName = savedDestination.name
                        destinationAddress = savedDestination.address
                        destinationLatitude = savedDestination.latitude
                        destinationLongitude = savedDestination.longitude
                        screenName = alarmSetupScreen.name
                    },
                    savedDestinations = destinationUiState.destinations,
                    onSavedDestinationRename = destinationViewModel::rename,
                    onSavedDestinationDeleteClick = destinationViewModel::delete,
                    isSaveInProgress = destinationUiState.isSaving,
                )
            }

            if (screen == AppScreen.AlarmSound) {
                AlarmSoundScreen(
                    selectedSound = alarmSound,
                    onBackClick = { screenName = alarmSetupScreen.name },
                    onSaveClick = { selectedSound ->
                        alarmSoundName = selectedSound.name
                        alarmSoundUri = selectedSound.uri
                        screenName = alarmSetupScreen.name
                    },
                )
            }
        }
    }
}

private enum class AppScreen {
    AlarmRinging,
    Home,
    AddAlarm,
    EditAlarm,
    Destination,
    AlarmSound,
    MyPage,
    Login,
    TermsAgreement,
    Settings,
    ActiveAlarmTracking,
}

private const val UnavailableEditingAlarmTime = "06:20"
private val DefaultSelectedDays = listOf("월", "화", "수", "목", "금", "토", "일")
private const val DefaultLimitMinutes = 13
internal fun alarmSetupInitialTime(
    editingAlarmId: String?,
    editingAlarmTime: String?,
    newAlarmInitialTime: String,
): String = when {
    editingAlarmId == null -> newAlarmInitialTime
    editingAlarmTime != null -> editingAlarmTime
    else -> UnavailableEditingAlarmTime
}

private fun AlarmScheduleRequest.toHomeAlarm(enabled: Boolean): HomeAlarm = HomeAlarm(
    id = id,
    time = time,
    days = if (repeatEnabled) weekdaySummary(selectedDays) else "한 번",
    destination = destinationName,
    timeLimitMinutes = limitMinutes,
    isEnabled = enabled,
    targetAddress = destinationAddress,
    targetLatitude = destinationLatitude,
    targetLongitude = destinationLongitude,
    alarmSoundName = alarmSoundName,
    alarmSoundUri = alarmSoundUri,
    selectedDays = selectedDays,
    repeatEnabled = repeatEnabled,
)
