package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.plusDays
import com.joon.ringout.presentation.roomhome.roomHomeColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.records_chevron

@Composable
internal fun RoomHomeWeekCalendar(
    selectedDate: MissionDate,
    visibleWeekStart: MissionDate,
    participantCounts: Map<MissionDate, Int>,
    onDateSelected: (MissionDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
    participantProfiles: Map<MissionDate, List<String?>> = emptyMap(),
) {
    val colors = roomHomeColors()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousWeek, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(Res.drawable.records_chevron),
                    contentDescription = "이전 주",
                    modifier = Modifier.size(20.dp).rotate(180f),
                    tint = colors.content,
                )
            }
            Text(
                text = selectedDate.iso8601.replace("-", ". "),
                modifier = Modifier.weight(1f)
                    .clickable(role = Role.Button, onClickLabel = "달력 열기", onClick = onOpenCalendar)
                    .padding(vertical = 12.dp),
                color = colors.content,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNextWeek, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(Res.drawable.records_chevron),
                    contentDescription = "다음 주",
                    modifier = Modifier.size(20.dp),
                    tint = colors.content,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(7) { index ->
                val date = visibleWeekStart.plusDays(index)
                val count = participantCounts[date]?.coerceAtLeast(0)
                val selected = date == selectedDate
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 49.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(if (selected) Modifier.background(colors.selectedDay) else Modifier)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onDateSelected(date) })
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${date.year}년 ${date.month}월 ${date.day}일, " +
                                if (count == null) "기록 미조회" else "목표 달성 ${count}명"
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = date.day.toString(),
                        color = colors.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    if (count != null && count > 0) {
                        RoomHomeParticipantAvatars(count = count, profileImageUrls = participantProfiles[date].orEmpty())
                    }
                }
            }
        }
    }
}

@Preview(name = "모임 주간 달력 · 다크", widthDp = 342)
@Composable
private fun RoomHomeWeekCalendarPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeWeekCalendar(
            selectedDate = RoomHomePreviewDate,
            visibleWeekStart = RoomHomePreviewDate.plusDays(-4),
            participantCounts = (0..5).associate { RoomHomePreviewDate.plusDays(-it) to it },
            onDateSelected = {}, onPreviousWeek = {}, onNextWeek = {}, onOpenCalendar = {},
            modifier = Modifier.background(roomHomeColors().background),
        )
    }
}

@Preview(name = "모임 주간 달력 · 라이트", widthDp = 342)
@Composable
private fun RoomHomeWeekCalendarLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeWeekCalendar(
            selectedDate = RoomHomePreviewDate,
            visibleWeekStart = RoomHomePreviewDate.plusDays(-4),
            participantCounts = (0..5).associate { RoomHomePreviewDate.plusDays(-it) to it },
            onDateSelected = {}, onPreviousWeek = {}, onNextWeek = {}, onOpenCalendar = {},
            modifier = Modifier.background(roomHomeColors().background),
        )
    }
}
