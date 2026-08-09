package com.joon.ringout.domain.missionhistory

data class MissionYearMonth(
    val year: Int,
    val month: Int,
) {
    init {
        require(year in 1..9999) { "Year must be between 1 and 9999." }
        require(month in 1..12) { "Month must be between 1 and 12." }
    }

    val dayCount: Int
        get() = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

    fun previous(): MissionYearMonth =
        if (month == 1) MissionYearMonth(year - 1, 12) else copy(month = month - 1)

    fun next(): MissionYearMonth =
        if (month == 12) MissionYearMonth(year + 1, 1) else copy(month = month + 1)
}

internal fun isLeapYear(year: Int): Boolean =
    year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
