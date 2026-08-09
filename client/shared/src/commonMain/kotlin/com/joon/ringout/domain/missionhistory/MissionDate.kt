package com.joon.ringout.domain.missionhistory

import kotlin.jvm.JvmInline

@JvmInline
value class MissionDate private constructor(
    val iso8601: String,
) {
    val year: Int get() = iso8601.substring(0, 4).toInt()
    val month: Int get() = iso8601.substring(5, 7).toInt()
    val day: Int get() = iso8601.substring(8, 10).toInt()

    fun belongsTo(month: MissionYearMonth): Boolean =
        year == month.year && this.month == month.month

    companion object {
        fun parse(value: String): MissionDate {
            require(value.length == 10 && value[4] == '-' && value[7] == '-') {
                "Mission date must use yyyy-MM-dd."
            }
            require(value.indices.all { index ->
                index == 4 || index == 7 || value[index].isDigit()
            }) { "Mission date must use yyyy-MM-dd." }

            val year = value.substring(0, 4).toInt()
            val month = value.substring(5, 7).toInt()
            val day = value.substring(8, 10).toInt()
            val yearMonth = MissionYearMonth(year, month)
            require(day in 1..yearMonth.dayCount) { "Mission date does not exist." }
            return MissionDate(value)
        }

        fun of(year: Int, month: Int, day: Int): MissionDate =
            parse(
                year.toString().padStart(4, '0') + "-" +
                    month.toString().padStart(2, '0') + "-" +
                    day.toString().padStart(2, '0'),
            )
    }
}
