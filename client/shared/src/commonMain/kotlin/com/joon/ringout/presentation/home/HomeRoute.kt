package com.joon.ringout.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.AlarmController
import com.joon.ringout.alarm.AlarmScheduleRequest

@Composable
internal fun HomeRoute(
    viewModel: HomeViewModel,
    alarmController: AlarmController,
    activeAlarmMission: ActiveAlarmMission?,
    onAddAlarm: () -> Unit,
    onEditAlarm: (AlarmScheduleRequest) -> Unit,
    onMyPageClick: () -> Unit,
    onActiveAlarmMissionClick: () -> Unit,
    onActiveAlarmMissionExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeScreen(
        uiState = viewModel.uiState,
        activeAlarmMission = activeAlarmMission,
        onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
        onAddAlarm = onAddAlarm,
        onAlarmClick = { alarmId ->
            val request = viewModel.alarmScheduleRequest(alarmId)
            if (request == null) {
                viewModel.showError("알람의 목적지 정보를 불러오지 못했습니다.")
            } else {
                onEditAlarm(request)
            }
        },
        onAlarmEnabledChange = { alarmId, enabled ->
            alarmController.perform(viewModel.onAlarmEnabledChange(alarmId, enabled))
        },
        onAlarmDelete = { alarmId ->
            alarmController.perform(viewModel.onAlarmDelete(alarmId))
        },
        onActiveAlarmMissionClick = onActiveAlarmMissionClick,
        onSettingsClick = onMyPageClick,
        modifier = modifier,
    )
}

private fun AlarmController.perform(command: HomeViewModel.Command) {
    when (command) {
        is HomeViewModel.Command.SetAlarmEnabled -> setEnabled(command.alarmId, command.enabled)
        is HomeViewModel.Command.DeleteAlarm -> deleteAlarm(command.alarmId)
    }
}
