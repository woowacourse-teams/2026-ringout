package com.joon.ringout.presentation.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joon.ringout.domain.missionhistory.MissionDate

@Composable
internal fun RecordsRoute(
    viewModel: RecordsViewModel,
    modifier: Modifier = Modifier,
    onRecordClick: (MissionDate) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    RecordsScreen(
        uiState = uiState,
        onDateSelected = viewModel::selectDate,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onOpenCalendar = viewModel::openCalendar,
        onCloseCalendar = viewModel::closeCalendar,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onRetry = viewModel::retry,
        onCalendarRetry = viewModel::retryCalendar,
        onRecordClick = onRecordClick,
        modifier = modifier,
    )
}
