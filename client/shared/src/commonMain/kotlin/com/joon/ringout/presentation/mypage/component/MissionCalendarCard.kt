package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.presentation.mypage.CalendarDayUiModel
import com.joon.ringout.presentation.mypage.MyPageCalendarMonth
import com.joon.ringout.presentation.mypage.buildCalendarCells
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.mypage_mission_stamp

@Composable
fun MissionCalendarCard(
    month: MyPageCalendarMonth,
    successDates: Set<MissionDate>,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells = remember(month, successDates) { buildCalendarCells(month, successDates) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CalendarHeader(
            title = "${month.value.year}년 ${month.value.month}월",
            onPreviousMonthClick = onPreviousMonthClick,
            onNextMonthClick = onNextMonthClick,
        )
        WeekdayHeader()
        cells.chunked(7).forEach { week -> CalendarWeek(week) }
    }
}

@Composable
private fun CalendarHeader(
    title: String,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthButton("‹", "이전 달", onPreviousMonthClick)
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        MonthButton("›", "다음 달", onNextMonthClick)
    }
}

@Composable
private fun MonthButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 34.sp)
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        Weekdays.forEach { weekday ->
            Text(
                text = weekday,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CalendarWeek(week: List<CalendarDayUiModel>) {
    Row(Modifier.fillMaxWidth()) {
        week.forEach { cell ->
            Column(
                modifier = Modifier.weight(1f).height(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (cell.day != null) {
                    Text(
                        text = cell.day.toString(),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (cell.isMissionSuccess) {
                        Image(
                            painter = painterResource(Res.drawable.mypage_mission_stamp),
                            contentDescription = null,
                            modifier = Modifier
                                .size(23.dp)
                                .semantics { contentDescription = "${cell.day}일 미션 성공" },
                        )
                    }
                }
            }
        }
    }
}

private val Weekdays = listOf("일", "월", "화", "수", "목", "금", "토")

@Preview(widthDp = 402)
@Composable
private fun MissionCalendarCardPreview() {
    RingoutTheme(ThemeMode.Light) {
        MissionCalendarCard(
            month = MyPageCalendarMonth(MissionYearMonth(2026, 8)),
            successDates = setOf(
                MissionDate.of(2026, 8, 1),
                MissionDate.of(2026, 8, 3),
            ),
            onPreviousMonthClick = {},
            onNextMonthClick = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}
