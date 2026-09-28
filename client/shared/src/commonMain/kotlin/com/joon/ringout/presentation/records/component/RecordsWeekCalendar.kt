package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.records.RecordsDayUiState
import com.joon.ringout.presentation.records.recordsColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.records_chevron

@Composable
internal fun RecordsWeekCalendar(
    selectedDate: MissionDate,
    days: List<RecordsDayUiState>,
    onDateSelected: (MissionDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = recordsColors()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousWeek) {
                Icon(painterResource(Res.drawable.records_chevron), "이전 주", Modifier.size(20.dp).rotate(180f), tint = colors.text)
            }
            Text(
                text = selectedDate.iso8601.replace("-", ". "),
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNextWeek) {
                Icon(painterResource(Res.drawable.records_chevron), "다음 주", Modifier.size(20.dp), tint = colors.text)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().selectableGroup().padding(top = 4.dp)) {
            days.forEach { day ->
                RecordsCalendarDay(day, { onDateSelected(day.date) }, Modifier.weight(1f))
            }
        }
    }
}

@Preview
@Composable
private fun RecordsWeekCalendarPreview() {
    val date = MissionDate.of(2026, 9, 28)
    RingoutTheme {
        RecordsWeekCalendar(
            date,
            (27..30).map { RecordsDayUiState(MissionDate.of(2026, 9, it), it == 28, it == 28, it > 28, null, 0) } +
                (1..3).map { RecordsDayUiState(MissionDate.of(2026, 10, it), false, false, true, null, 0) },
            {}, {}, {},
        )
    }
}
