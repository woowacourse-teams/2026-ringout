package com.joon.ringout.presentation.records.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.presentation.records.RecordsDayUiState
import com.joon.ringout.presentation.records.recordsColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.mypage_mission_stamp
import ringout.shared.generated.resources.records_failure

@Composable
internal fun RecordsCalendarDay(
    day: RecordsDayUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = recordsColors()
    val shape = RoundedCornerShape(10.dp)
    val description = buildString {
        append("${day.date.year}년 ${day.date.month}월 ${day.date.day}일")
        if (day.isToday) append(", 오늘")
        append(", 기록 ${day.recordCount}개")
        when (day.result) {
            MissionResult.SUCCESS -> append(", 최근 기록 목적지 도착")
            MissionResult.FAILURE -> append(", 최근 기록 미션 실패")
            null -> Unit
        }
    }
    Column(
        modifier = modifier.heightIn(min = 49.dp)
            .clip(shape)
            .then(if (day.isSelected) Modifier.background(colors.selectedDay) else Modifier)
            .then(if (day.isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .selectable(selected = day.isSelected, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            day.date.day.toString(),
            fontSize = 14.sp,
            lineHeight = 16.sp,
            color = if (day.isFuture) colors.secondaryText else colors.text,
        )
        when (day.result) {
            MissionResult.SUCCESS -> Image(
                painterResource(Res.drawable.mypage_mission_stamp), null, modifier = Modifier.size(23.dp),
            )
            MissionResult.FAILURE -> Icon(
                painterResource(Res.drawable.records_failure), null,
                tint = colors.failure, modifier = Modifier.size(23.dp),
            )
            null -> Unit
        }
    }
}

@Preview
@Composable
private fun RecordsCalendarDayPreview() {
    RingoutTheme {
        RecordsCalendarDay(
            RecordsDayUiState(MissionDate.of(2026, 9, 28), true, true, false, MissionResult.SUCCESS, 2),
            {}, Modifier.size(49.dp),
        )
    }
}
