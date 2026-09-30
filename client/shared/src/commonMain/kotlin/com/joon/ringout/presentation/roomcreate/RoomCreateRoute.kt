package com.joon.ringout.presentation.roomcreate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.joon.ringout.presentation.currentLocalClockSnapshot
import com.joon.ringout.presentation.roomcreate.model.RoomCreateDraft
import com.joon.ringout.presentation.to24HourTimeString

@Composable
internal fun RoomCreateRoute(
    viewModel: RoomCreateViewModel,
    onBackClick: () -> Unit,
    onCreateDraft: (RoomCreateDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel) {
        viewModel.initializeTimeIfNeeded(currentLocalClockSnapshot().to24HourTimeString())
    }

    RoomCreateScreen(
        uiState = viewModel.uiState,
        onBackClick = { viewModel.onBack(onBackClick) },
        onNameChange = viewModel::updateName,
        onIntroductionChange = viewModel::updateIntroduction,
        onDayClick = viewModel::toggleDay,
        onAmPmChange = viewModel::updateAmPm,
        onHourChange = viewModel::updateHour,
        onMinuteChange = viewModel::updateMinute,
        onAction = {
            if (viewModel.uiState.step < 3) {
                viewModel.goToNextStep()
            } else {
                viewModel.createDraft()?.let(onCreateDraft)
            }
        },
        modifier = modifier,
    )
}
