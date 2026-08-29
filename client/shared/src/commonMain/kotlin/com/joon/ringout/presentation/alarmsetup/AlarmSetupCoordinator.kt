package com.joon.ringout.presentation.alarmsetup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.joon.ringout.alarm.AlarmController
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.alarmsetup.components.MissionLocationPermissionDialog
import com.joon.ringout.presentation.common.canShowAppDialog
import com.joon.ringout.presentation.navigation.AppRoute

@Composable
internal fun AlarmSetupCoordinator(
    alarmController: AlarmController,
    viewModel: AlarmSetupViewModel?,
    displayedRoute: AppRoute,
    authSessionState: AuthSessionState,
    missionLocationState: MissionLocationState,
    useSystemLocationPermissionUiOnly: Boolean,
    onRequestWhenInUseLocation: () -> Unit,
    onRequestAlwaysLocation: () -> Unit,
    onConfirmAlwaysLocationResult: () -> Unit,
    onRequestTemporaryFullAccuracy: () -> Unit,
) {
    val currentRequestWhenInUseLocation = rememberUpdatedState(onRequestWhenInUseLocation)
    val currentRequestAlwaysLocation = rememberUpdatedState(onRequestAlwaysLocation)
    val currentConfirmAlwaysLocationResult = rememberUpdatedState(onConfirmAlwaysLocationResult)
    val currentRequestTemporaryFullAccuracy = rememberUpdatedState(onRequestTemporaryFullAccuracy)
    val commandExecutor = remember(alarmController) {
        AlarmSetupCommandExecutor(
            scheduleAlarm = alarmController.schedule,
            requestWhenInUseLocation = { currentRequestWhenInUseLocation.value() },
            requestAlwaysLocation = { currentRequestAlwaysLocation.value() },
            confirmAlwaysLocationResult = { currentConfirmAlwaysLocationResult.value() },
            requestTemporaryFullAccuracy = { currentRequestTemporaryFullAccuracy.value() },
        )
    }

    AlarmSetupCommandEffect(
        commandExecutor = commandExecutor,
        viewModel = viewModel,
        displayedRoute = displayedRoute,
        authSessionState = authSessionState,
        missionLocationState = missionLocationState,
        useSystemLocationPermissionUiOnly = useSystemLocationPermissionUiOnly,
    )
    AlarmSetupPermissionDialog(
        commandExecutor = commandExecutor,
        viewModel = viewModel,
        displayedRoute = displayedRoute,
        authSessionState = authSessionState,
        useSystemLocationPermissionUiOnly = useSystemLocationPermissionUiOnly,
    )
}

@Composable
private fun AlarmSetupCommandEffect(
    commandExecutor: AlarmSetupCommandExecutor,
    viewModel: AlarmSetupViewModel?,
    displayedRoute: AppRoute,
    authSessionState: AuthSessionState,
    missionLocationState: MissionLocationState,
    useSystemLocationPermissionUiOnly: Boolean,
) {
    val uiState = viewModel?.uiState
    LaunchedEffect(
        viewModel,
        uiState?.pendingSaveRequest?.id,
        uiState?.isScheduling,
        missionLocationState.revision,
        displayedRoute,
        authSessionState,
        useSystemLocationPermissionUiOnly,
        commandExecutor,
    ) {
        commandExecutor.execute(
            viewModel?.onLocationStateChanged(
                locationState = missionLocationState,
                useSystemPermissionUiOnly = useSystemLocationPermissionUiOnly,
                canProcessSave =
                    displayedRoute !is AppRoute.AlarmRinging &&
                        authSessionState != AuthSessionState.ReauthenticationRequired,
            ),
        )
    }
}

@Composable
private fun AlarmSetupPermissionDialog(
    commandExecutor: AlarmSetupCommandExecutor,
    viewModel: AlarmSetupViewModel?,
    displayedRoute: AppRoute,
    authSessionState: AuthSessionState,
    useSystemLocationPermissionUiOnly: Boolean,
) {
    if (
        !useSystemLocationPermissionUiOnly &&
        canShowAppDialog(displayedRoute, authSessionState)
    ) {
        MissionLocationPermissionDialog(
            decision = viewModel?.permissionDialog,
            onConfirm = {
                commandExecutor.execute(viewModel?.onPermissionDialogConfirmed())
            },
            onDismiss = { viewModel?.onPermissionDialogDismissed() },
        )
    }
}

internal class AlarmSetupCommandExecutor(
    private val scheduleAlarm: (AlarmScheduleRequest) -> Unit,
    private val requestWhenInUseLocation: () -> Unit,
    private val requestAlwaysLocation: () -> Unit,
    private val confirmAlwaysLocationResult: () -> Unit,
    private val requestTemporaryFullAccuracy: () -> Unit,
) {
    fun execute(command: AlarmSetupViewModel.Command?) {
        when (command) {
            is AlarmSetupViewModel.Command.ScheduleAlarm -> scheduleAlarm(command.request)
            AlarmSetupViewModel.Command.RequestWhenInUseLocation -> requestWhenInUseLocation()
            AlarmSetupViewModel.Command.RequestAlwaysLocation -> requestAlwaysLocation()
            AlarmSetupViewModel.Command.ConfirmAlwaysLocationResult -> confirmAlwaysLocationResult()
            AlarmSetupViewModel.Command.RequestTemporaryFullAccuracy ->
                requestTemporaryFullAccuracy()
            null -> Unit
        }
    }
}
