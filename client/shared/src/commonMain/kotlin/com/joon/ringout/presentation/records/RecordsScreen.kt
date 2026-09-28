package com.joon.ringout.presentation.records

import com.joon.ringout.domain.alarmactivity.AlarmActivitySummary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.presentation.records.component.RecordHistoryCard
import com.joon.ringout.presentation.records.component.RecordsHeader
import com.joon.ringout.presentation.records.component.RecordsMonthDialog
import com.joon.ringout.presentation.records.component.RecordsStateContent
import com.joon.ringout.presentation.records.component.RecordsSummary
import com.joon.ringout.presentation.records.component.RecordsWeekCalendar

@Composable
fun RecordsScreen(
    uiState: RecordsUiState,
    modifier: Modifier = Modifier,
    onDateSelected: (MissionDate) -> Unit = {},
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onCloseCalendar: () -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onRetry: () -> Unit = {},
    onCalendarRetry: () -> Unit = {},
) {
    // Legacy rows have no occurrence ID. A reverse index remains stable when a new result is appended.
    val recordKeys = uiState.records.mapIndexed { index, record ->
        record.occurrenceId ?: "${record.completedAt.iso8601}-legacy-${uiState.records.lastIndex - index}"
    }
    var expandedRecordKeys by rememberSaveable(uiState.selectedDate.iso8601) { mutableStateOf<List<String>?>(null) }
    val expandedRecords = expandedRecordKeys ?: recordKeys.take(1)
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.selectedDate) { listState.scrollToItem(0) }
    Column(
        modifier = modifier.fillMaxSize().background(recordsColors().background)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        RecordsHeader(
            onOpenCalendar = onOpenCalendar,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        )
        RecordsWeekCalendar(
            selectedDate = uiState.selectedDate,
            days = uiState.weekDays,
            onDateSelected = onDateSelected,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            modifier = Modifier.padding(horizontal = 30.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 30.dp),
            state = listState,
            contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                RecordsSummary(
                    summary = uiState.activitySummary,
                    isLoading = uiState.isSummaryLoading,
                    errorMessage = uiState.summaryErrorMessage,
                    onRetry = onRetry,
                )
            }
            if (uiState.isLoading || uiState.errorMessage != null) {
                item { RecordsStateContent(uiState.isLoading, uiState.errorMessage, onRetry) }
            } else {
                if (uiState.records.isEmpty()) item { RecordsStateContent(false, null, onRetry) }
                itemsIndexed(uiState.records, key = { index, _ -> recordKeys[index] }) { index, record ->
                    RecordHistoryCard(
                        record = record,
                        number = index + 1,
                        expanded = recordKeys[index] in expandedRecords,
                        onExpandedChange = {
                            val key = recordKeys[index]
                            expandedRecordKeys = if (key in expandedRecords) expandedRecords - key else expandedRecords + key
                        },
                    )
                }
            }
        }
    }
    if (uiState.isCalendarVisible) {
        RecordsMonthDialog(
            month = uiState.calendarMonth,
            days = uiState.calendarDays,
            isLoading = uiState.isCalendarLoading,
            errorMessage = uiState.calendarErrorMessage,
            onDateSelected = onDateSelected,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onDismiss = onCloseCalendar,
            onRetry = onCalendarRetry,
        )
    }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun RecordsScreenPreview() {
    RingoutTheme { RecordsScreen(previewRecordsState()) }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun RecordsScreenLightPreview() {
    RingoutTheme(ThemeMode.Light) { RecordsScreen(previewRecordsState()) }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun RecordsScreenEmptyPreview() {
    RingoutTheme { RecordsScreen(previewRecordsState().copy(records = emptyList(), activitySummary = AlarmActivitySummary(ringingCount = 0))) }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun RecordsScreenLoadingPreview() {
    RingoutTheme { RecordsScreen(previewRecordsState().copy(isLoading = true, records = emptyList(), isSummaryLoading = true)) }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun RecordsScreenErrorPreview() {
    RingoutTheme { RecordsScreen(previewRecordsState().copy(errorMessage = "기록을 불러오지 못했어요")) }
}

private fun previewRecordsState(): RecordsUiState {
    val date = MissionDate.of(2026, 9, 28)
    return RecordsUiState(
        today = date,
        selectedDate = date,
        activitySummary = AlarmActivitySummary(ringingCount = 5),
        isLoading = false,
        weekDays = (27..30).map {
            RecordsDayUiState(MissionDate.of(2026, 9, it), it == 28, it == 28, it > 28, if (it <= 28) MissionResult.SUCCESS else null, if (it <= 28) 2 else 0)
        } + (1..3).map { RecordsDayUiState(MissionDate.of(2026, 10, it), false, false, true, null, 0) },
        records = listOf(
            MissionHistoryEntry(
                MissionResult.SUCCESS, date, "preview-success",
                ringingStartedAtEpochMillis = 1790546400000L,
                ringingStoppedAtEpochMillis = 1790546700000L,
                missionCompletedAtEpochMillis = 1790547480000L,
            ),
            MissionHistoryEntry(MissionResult.FAILURE, date, "preview-failure"),
        ),
    )
}
