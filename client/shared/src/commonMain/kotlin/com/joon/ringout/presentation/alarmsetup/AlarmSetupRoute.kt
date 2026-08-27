package com.joon.ringout.presentation.alarmsetup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun AlarmSetupRoute(
    viewModel: AlarmSetupViewModel,
    isActive: Boolean,
    onBackClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onAlarmSoundClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlarmSetupScreen(
        uiState = viewModel.uiState,
        onAmPmChange = { isAm ->
            if (isActive) viewModel.updateAmPm(isAm)
        },
        onHourChange = { hour ->
            if (isActive) viewModel.updateHour(hour)
        },
        onMinuteChange = { minute ->
            if (isActive) viewModel.updateMinute(minute)
        },
        onDayClick = { day ->
            if (isActive) viewModel.toggleDay(day)
        },
        onLimitMinutesChange = { minutes ->
            if (isActive) viewModel.updateLimitMinutes(minutes)
        },
        onBackClick = {
            if (isActive && !viewModel.uiState.isSaveInProgress) onBackClick()
        },
        onDestinationClick = {
            if (isActive) onDestinationClick()
        },
        onAlarmSoundClick = {
            if (isActive) onAlarmSoundClick()
        },
        onSaveClick = {
            if (isActive) viewModel.requestSave()
        },
        modifier = modifier,
    )
}
