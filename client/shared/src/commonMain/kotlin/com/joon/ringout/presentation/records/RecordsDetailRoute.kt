package com.joon.ringout.presentation.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joon.ringout.domain.missionhistory.AlarmRecordShareData
import com.joon.ringout.domain.missionhistory.MissionDate

@Composable
internal fun RecordsDetailRoute(
    viewModel: RecordsViewModel,
    date: MissionDate,
    onBack: () -> Unit,
    onShare: (AlarmRecordShareData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel, date) {
        viewModel.selectDate(date)
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val matchesDate = state.selectedDate == date
    val error = state.weekChangeErrorMessage ?: if (matchesDate) state.errorMessage else null
    RecordsDetailScreen(
        date = date,
        records = if (matchesDate && !state.isWeekChanging) state.records else emptyList(),
        isLoading = error == null && (!matchesDate || state.isLoading || state.isWeekChanging),
        errorMessage = error,
        onRetry = viewModel::retry,
        onBack = onBack,
        onShare = onShare,
        modifier = modifier,
    )
}
