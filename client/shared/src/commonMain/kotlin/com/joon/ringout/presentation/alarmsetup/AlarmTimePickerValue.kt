package com.joon.ringout.presentation.alarmsetup

internal data class AlarmTimePickerValue(
    val isAm: Boolean,
    val hour: Int,
    val minute: Int,
)

internal fun String.toAlarmTimePickerValue(): AlarmTimePickerValue {
    val rawHour = substringBefore(":").toIntOrNull() ?: 0
    val minute = substringAfter(":", "00").toIntOrNull()?.coerceIn(0, 59) ?: 0
    val hour24 = ((rawHour % HoursPerDay) + HoursPerDay) % HoursPerDay
    val hour12 = (hour24 % HoursPerPeriod).takeIf { it != 0 } ?: HoursPerPeriod

    return AlarmTimePickerValue(
        isAm = hour24 < HoursPerPeriod,
        hour = hour12,
        minute = minute,
    )
}

internal fun AlarmTimePickerValue.to24HourString(): String {
    val hour12 = hour.coerceIn(1, HoursPerPeriod)
    val hour24 = when {
        isAm && hour12 == HoursPerPeriod -> 0
        isAm -> hour12
        hour12 == HoursPerPeriod -> HoursPerPeriod
        else -> hour12 + HoursPerPeriod
    }

    return "${hour24.twoDigits()}:${minute.coerceIn(0, 59).twoDigits()}"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

private const val HoursPerPeriod = 12
private const val HoursPerDay = 24
