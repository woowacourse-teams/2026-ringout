package com.joon.ringout.presentation.mypage.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.presentation.mypage.CalendarCellCount
import com.joon.ringout.presentation.mypage.CalendarDayUiModel
import com.joon.ringout.presentation.mypage.CalendarWeekCount
import com.joon.ringout.presentation.mypage.DaysPerWeek
import com.joon.ringout.presentation.mypage.MyPageCalendarMonth
import com.joon.ringout.presentation.mypage.buildCalendarCells
import org.jetbrains.compose.resources.painterResource

@Composable
fun MissionCalendarCard(
    month: MyPageCalendarMonth,
    successDates: Set<MissionDate>,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = myPageColors()
    val shape = RoundedCornerShape(CalendarCornerRadius)
    val cells = remember(month, successDates) { buildCalendarCells(month, successDates) }

    Column(
        modifier = modifier
            .widthIn(max = CalendarMaxWidth)
            .fillMaxWidth()
            .height(CalendarHeight)
            .background(colors.calendarSurface, shape)
            .border(CalendarBorderWidth, colors.calendarBorder, shape)
            .padding(CalendarPadding),
    ) {
        CalendarHeader(
            title = "${month.value.year}년 ${month.value.month}월",
            contentColor = colors.primaryText,
            onPreviousMonthClick = onPreviousMonthClick,
            onNextMonthClick = onNextMonthClick,
        )
        Spacer(Modifier.height(HeaderBodySpacing))
        CalendarBody(
            cells = cells,
            primaryTextColor = colors.primaryText,
            secondaryTextColor = colors.secondaryText,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CalendarHeader(
    title: String,
    contentColor: Color,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthButton(
            direction = ChevronDirection.Previous,
            description = "이전 달",
            contentColor = contentColor,
            onClick = onPreviousMonthClick,
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = contentColor,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        MonthButton(
            direction = ChevronDirection.Next,
            description = "다음 달",
            contentColor = contentColor,
            onClick = onNextMonthClick,
        )
    }
}

@Composable
private fun MonthButton(
    direction: ChevronDirection,
    description: String,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(ChevronVisualSize)
            .clickable(
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(MyPageChevronRightIconResource),
            contentDescription = null,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = Modifier
                .size(ChevronVisualSize)
                .graphicsLayer {
                    rotationZ = if (direction == ChevronDirection.Previous) 180f else 0f
                },
        )
    }
}

@Composable
private fun CalendarBody(
    cells: List<CalendarDayUiModel>,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier,
) {
    require(cells.size == CalendarCellCount)

    Column(modifier = modifier.fillMaxWidth()) {
        WeekdayHeader(contentColor = secondaryTextColor)
        Spacer(Modifier.height(WeekdayWeeksSpacing))
        cells.chunked(DaysPerWeek).forEachIndexed { index, week ->
            CalendarWeek(
                week = week,
                contentColor = primaryTextColor,
                modifier = Modifier.weight(1f),
            )
            if (index < CalendarWeekCount - 1) {
                Spacer(Modifier.height(WeekSpacing))
            }
        }
    }
}

@Composable
private fun WeekdayHeader(contentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(WeekdayHeight),
    ) {
        Weekdays.forEach { weekday ->
            Text(
                text = weekday,
                modifier = Modifier.weight(1f),
                color = contentColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CalendarWeek(
    week: List<CalendarDayUiModel>,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        week.forEach { cell ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                cell.day?.let { day ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = day.toString(),
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    if (cell.isMissionSuccess) {
                        Spacer(Modifier.height(2.dp))
                        MissionResultIndicator(day = day)
                    }
                }
            }
        }
    }
}

private enum class ChevronDirection {
    Previous,
    Next,
}

private val Weekdays = listOf("일", "월", "화", "수", "목", "금", "토")

private val CalendarMaxWidth = 361.dp
private val CalendarHeight = 372.dp
private val CalendarCornerRadius = 16.dp
private val CalendarBorderWidth = 1.dp
private val CalendarPadding = 16.dp
private val HeaderHeight = 24.dp
private val HeaderBodySpacing = 16.dp
private val ChevronVisualSize = 20.dp
private val WeekdayHeight = 20.dp
private val WeekdayWeeksSpacing = 8.dp
private val WeekSpacing = 4.dp

@Preview(widthDp = 402, heightDp = 412)
@Composable
private fun MissionCalendarCardPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MissionCalendarCard(
            month = MyPageCalendarMonth(MissionYearMonth(2026, 8)),
            successDates = setOf(
                MissionDate.of(2026, 8, 1),
                MissionDate.of(2026, 8, 3),
            ),
            onPreviousMonthClick = {},
            onNextMonthClick = {},
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        )
    }
}
