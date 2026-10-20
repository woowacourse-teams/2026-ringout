package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.alarm.rememberAlarmController
import com.joon.ringout.analytics.AlarmSettingsAnalyticsContext
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.DestinationSelectionSource
import com.joon.ringout.presentation.destination.component.DestinationEntry
import com.joon.ringout.analytics.AnalyticsAlarmSoundSurface
import com.joon.ringout.di.AppContainer
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.alarmsound.resolveInitialAlarmSoundSelection
import com.joon.ringout.presentation.alarmsound.resolveAlarmSoundDisplaySelection
import com.joon.ringout.presentation.alarmsetup.AlarmSetupCoordinator
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import com.joon.ringout.presentation.alarmsetup.DefaultAlarmTime
import com.joon.ringout.presentation.alarmsetup.rememberDeviceAlarmSoundController
import com.joon.ringout.presentation.common.component.AppMessageHost
import com.joon.ringout.presentation.common.component.AppMessageHostState
import com.joon.ringout.presentation.destination.DefaultDestinationSelection
import com.joon.ringout.presentation.destination.DestinationRoute
import com.joon.ringout.presentation.destination.DestinationManagementRoute
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.destination.DestinationCurrentLocationCache
import com.joon.ringout.presentation.destination.PlatformBackHandler
import com.joon.ringout.presentation.destination.toDestinationSelection
import com.joon.ringout.presentation.navigation.AppRoute

@Composable
internal fun OnboardingRoute(
    appContainer: AppContainer,
    destinationCurrentLocationCache: DestinationCurrentLocationCache,
    missionLocationState: MissionLocationState,
    useSystemLocationPermissionUiOnly: Boolean,
    onRequestWhenInUseLocation: () -> Unit,
    onRequestAlwaysLocation: () -> Unit,
    onConfirmAlwaysLocationResult: () -> Unit,
    onRequestTemporaryFullAccuracy: () -> Unit,
    onComplete: (stepCount: Int) -> Unit,
    modifier: Modifier = Modifier,
    completionEnabled: Boolean = true,
    completionRetryToken: Int = 0,
) {
    val flow = viewModel {
        OnboardingViewModel(
            includesSoundSelection = PlatformOnboardingIncludesSoundSelection,
            analytics = appContainer.productAnalyticsRecorder,
        )
    }
    val editor = viewModel(key = "onboarding-alarm") {
        AlarmSetupViewModel().apply {
            startCreating(DefaultAlarmTime)
            updateLimitMinutes(5)
        }
    }
    val destinations = viewModel(key = "onboarding-destinations") {
        DestinationViewModel(
            appContainer.destinationRepository,
            appContainer.productAnalyticsRecorder,
            currentLocationCache = destinationCurrentLocationCache,
        )
    }
    val currentComplete = rememberUpdatedState(onComplete)
    val currentRetryToken = rememberUpdatedState(completionRetryToken)
    val controller = rememberAlarmController(
        onSaveCompleted = { request ->
            if (editor.onSaveCompleted(request) &&
                flow.onAlarmSaved(currentRetryToken.value) == OnboardingAdvance.Complete
            ) {
                currentComplete.value(flow.uiState.steps.size)
            }
        },
        onSaveError = editor::onSaveError,
        onError = { message ->
            val request = editor.uiState.pendingSaveRequest
            if (request != null) editor.onSaveError(request, message) else editor.showError(message)
        },
    )
    val soundController = rememberDeviceAlarmSoundController()
    val state = flow.uiState
    var soundStepInitialSelection by remember { mutableStateOf<AlarmSoundSelection?>(null) }
    LaunchedEffect(state.step, state.isDestinationOpen) {
        flow.onStepVisible()
    }
    LaunchedEffect(state.step) {
        soundStepInitialSelection = if (state.step == OnboardingStep.Sound) {
            editor.uiState.alarmSound
        } else {
            null
        }
    }
    var managementOpen by rememberSaveable { mutableStateOf(false) }
    var destinationDialogRequestId by rememberSaveable { mutableStateOf(0L) }
    val editable = !editor.uiState.isSaveInProgress && completionEnabled && !state.isAlarmSaved
    val sounds = soundController.sounds.ifEmpty { listOf(editor.uiState.alarmSound) }
    LaunchedEffect(sounds) {
        if (editable) {
            editor.updateAlarmSound(resolveInitialAlarmSoundSelection(sounds, editor.uiState.alarmSound))
        }
    }
    DisposableEffect(soundController, state.step, editable) {
        onDispose(soundController::stopPreview)
    }
    val savedEvent = destinations.uiState.savedEvent
    LaunchedEffect(savedEvent?.eventId) {
        savedEvent?.let { event ->
            if (flow.acceptDestination(event.requestId)) {
                editor.updateDestination(event.destination.toDestinationSelection())
            }
            destinations.consumeSavedEvent(event.eventId)
        }
    }
    PlatformBackHandler(enabled = !managementOpen && !state.isDestinationOpen && state.step != OnboardingStep.Time) {
        if (editable) flow.back()
    }
    if (managementOpen) {
        DestinationManagementRoute(
            viewModel = destinations,
            onFinished = {
                managementOpen = false
                destinationDialogRequestId += 1
            },
            modifier = modifier,
        )
    } else if (state.isDestinationOpen) {
        DestinationRoute(
            viewModel = destinations,
            initialSelection = editor.uiState.destination ?: DefaultDestinationSelection,
            requestId = state.destinationRequestId,
            authSessionState = AuthSessionState.Unauthenticated,
            productAnalyticsRecorder = appContainer.productAnalyticsRecorder,
            isActive = true,
            onBackClick = flow::back,
            onSavedDestinationConfirmClick = { destination ->
                if (flow.acceptDestination(state.destinationRequestId)) {
                    editor.updateDestination(destination.toDestinationSelection())
                }
            },
            modifier = modifier,
        )
    } else {
        DestinationEntry(
            uiState = destinations.uiState,
            isActive = editable,
            onFindInMap = flow::openDestination,
            onSelect = { destination ->
                if (editable) {
                    editor.updateDestination(destination.toDestinationSelection())
                    appContainer.productAnalyticsRecorder.recordDestinationSelected(
                        DestinationSelectionSource.SavedDestinationDialog,
                        AnalyticsLoginState.LoggedOut,
                    )
                }
            },
            onRetry = destinations::onScreenEntered,
            onManage = { destination ->
                if (editable && destinations.beginManagement(destination.id)) managementOpen = true
            },
            openRequestId = destinationDialogRequestId,
        ) { onDestinationClick ->
            OnboardingScreen(
                uiState = state,
                alarm = editor.uiState,
                sounds = sounds,
                onAmPmChange = { if (editable) editor.updateAmPm(it) },
                onHourChange = { if (editable) editor.updateHour(it) },
                onMinuteChange = { if (editable) editor.updateMinute(it) },
                onDayClick = { if (editable) editor.toggleDay(it) },
                onDestinationClick = onDestinationClick,
                onLimitMinutesChange = { if (editable) editor.updateLimitMinutes(it) },
                onSoundClick = {
                    if (editable) {
                        editor.updateAlarmSound(it)
                        soundController.preview(it)
                    }
                },
                onBack = { if (editable) flow.back() },
                onNext = {
                    if (completionEnabled) {
                        soundController.stopPreview()
                        when (flow.requestNext(editor.uiState, completionRetryToken)) {
                            OnboardingAdvance.SaveAlarm -> {
                                if (state.step == OnboardingStep.Sound) {
                                    editor.updateAlarmSoundAnalyticsContext(
                                        onboardingSoundAnalyticsContext(
                                            sounds = sounds,
                                            initialSelection = soundStepInitialSelection
                                                ?: editor.uiState.alarmSound,
                                            selectedSound = editor.uiState.alarmSound,
                                        ),
                                    )
                                }
                                if (editor.requestSave()) flow.onSubmitAccepted()
                            }
                            OnboardingAdvance.Complete -> {
                                flow.onSubmitAccepted()
                                onComplete(state.steps.size)
                            }
                            OnboardingAdvance.None -> Unit
                        }
                    }
                },
                modifier = modifier,
                completionEnabled = completionEnabled,
                completionFailed = state.isAlarmSaved && completionRetryToken > 0,
            )
        }
    }
    AlarmSetupCoordinator(
        alarmController = controller,
        viewModel = editor,
        displayedRoute = AppRoute.Onboarding,
        authSessionState = AuthSessionState.Unauthenticated,
        missionLocationState = missionLocationState,
        useSystemLocationPermissionUiOnly = useSystemLocationPermissionUiOnly,
        onRequestWhenInUseLocation = onRequestWhenInUseLocation,
        onRequestAlwaysLocation = onRequestAlwaysLocation,
        onConfirmAlwaysLocationResult = onConfirmAlwaysLocationResult,
        onRequestTemporaryFullAccuracy = onRequestTemporaryFullAccuracy,
    )
    val error = when {
        managementOpen -> null
        state.isDestinationOpen -> destinations.uiState.errorMessage
        else -> editor.uiState.errorMessage
    }
    AppMessageHost(
        state = error?.let { AppMessageHostState("설정을 저장하지 못했어요", it) },
        onDismiss = { if (state.isDestinationOpen) destinations.clearError() else editor.clearError() },
    )
}

private fun onboardingSoundAnalyticsContext(
    sounds: List<AlarmSoundSelection>,
    initialSelection: AlarmSoundSelection,
    selectedSound: AlarmSoundSelection,
): AlarmSettingsAnalyticsContext {
    val displaySelection = resolveAlarmSoundDisplaySelection(
        sounds = sounds,
        initialSelection = initialSelection,
        selectedSound = selectedSound,
        surface = AnalyticsAlarmSoundSurface.OnboardingStep,
    ) ?: return AlarmSettingsAnalyticsContext()
    return AlarmSettingsAnalyticsContext(
        soundDisplaySelection = displaySelection,
    )
}
