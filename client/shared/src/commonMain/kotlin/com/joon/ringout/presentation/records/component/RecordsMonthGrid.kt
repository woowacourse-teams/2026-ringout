package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.calendarDates
import com.joon.ringout.presentation.records.RecordsDayUiState

@Composable
internal fun RecordsMonthGrid(
    days: List<RecordsDayUiState?>,
    onDateSelected: (MissionDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayHeight = recordsCalendarDayHeight()
    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Six weeks fit every month. Blank weeks retain their height without becoming selectable.
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth().height(dayHeight)) {
                repeat(7) { weekday ->
                    val day = days.getOrNull(week * 7 + weekday)
                    if (day == null) Box(Modifier.weight(1f))
                    else RecordsCalendarDay(day, { onDateSelected(day.date) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Preview
@Composable
private fun RecordsMonthGridPreview() {
    RingoutTheme {
        RecordsMonthGrid(
            days = MissionYearMonth(2026, 2).calendarDates().map { date ->
                date?.let { RecordsDayUiState(it, false, false, false, null, 0) }
            },
            onDateSelected = {},
        )
    }
}
