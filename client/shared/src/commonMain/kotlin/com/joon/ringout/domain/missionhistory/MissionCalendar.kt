package com.joon.ringout.domain.missionhistory

val MissionDate.yearMonth: MissionYearMonth
    get() = MissionYearMonth(year, month)

/** Sunday is 0 and Saturday is 6 in the Gregorian calendar. */
val MissionDate.dayOfWeekIndex: Int
    get() {
        val precedingYear = year - 1
        val precedingDays = 365 * precedingYear + precedingYear / 4 -
            precedingYear / 100 + precedingYear / 400 +
            (1 until month).sumOf { MissionYearMonth(year, it).dayCount } + day - 1
        return (precedingDays + 1) % 7
    }

fun MissionDate.plusDays(days: Int): MissionDate {
    var targetMonth = yearMonth
    var targetDay = day.toLong() + days
    while (targetDay < 1) {
        targetMonth = targetMonth.previous()
        targetDay += targetMonth.dayCount
    }
    while (targetDay > targetMonth.dayCount) {
        targetDay -= targetMonth.dayCount
        targetMonth = targetMonth.next()
    }
    return MissionDate.of(targetMonth.year, targetMonth.month, targetDay.toInt())
}

fun MissionDate.weekDates(): List<MissionDate> {
    val sunday = plusDays(-dayOfWeekIndex)
    return List(7) { sunday.plusDays(it) }
}

/** Pads the current month into complete Sunday-first rows. */
fun MissionYearMonth.calendarDates(): List<MissionDate?> = buildList {
    repeat(MissionDate.of(year, month, 1).dayOfWeekIndex) { add(null) }
    for (day in 1..dayCount) add(MissionDate.of(year, month, day))
    while (size % 7 != 0) add(null)
}

fun MissionDate.isAfter(other: MissionDate): Boolean = iso8601 > other.iso8601
