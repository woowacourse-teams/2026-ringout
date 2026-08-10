package com.joon.ringout.presentation.mypage

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth

data class MyPageCalendarMonth(
    val value: MissionYearMonth,
    val firstDayOfWeek: Int = firstDayOfWeek(value),
    val dayCount: Int = value.dayCount,
)

data class CalendarDayUiModel(
    val day: Int?,
    val result: MissionResult?,
)

fun buildCalendarCells(
    month: MyPageCalendarMonth,
    resultsByDate: Map<MissionDate, MissionResult>,
): List<CalendarDayUiModel> = buildList {
    repeat(month.firstDayOfWeek) {
        add(CalendarDayUiModel(day = null, result = null))
    }
    for (day in 1..month.dayCount) {
        val date = MissionDate.of(
            year = month.value.year,
            month = month.value.month,
            day = day,
        )
        add(
            CalendarDayUiModel(
                day = day,
                result = resultsByDate[date],
            ),
        )
    }
    while (size % DaysPerWeek != 0) {
        add(CalendarDayUiModel(day = null, result = null))
    }
}

private fun firstDayOfWeek(month: MissionYearMonth): Int {
    var year = month.year
    var adjustedMonth = month.month
    if (adjustedMonth < 3) {
        adjustedMonth += 12
        year -= 1
    }
    val yearOfCentury = year % 100
    val century = year / 100
    val saturdayBased = (
        1 + (13 * (adjustedMonth + 1)) / 5 + yearOfCentury +
            yearOfCentury / 4 + century / 4 + 5 * century
        ) % 7
    return (saturdayBased + 6) % 7
}

internal expect fun currentMissionYearMonth(): MissionYearMonth

private const val DaysPerWeek = 7
