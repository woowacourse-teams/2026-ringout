package com.joon.ringout.presentation.home

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

internal actual fun currentLocalClockSnapshot(): LocalClockSnapshot {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "EEE|HH|mm"
    }
    val values = formatter.stringFromDate(NSDate()).split("|")

    return LocalClockSnapshot(
        dayOfWeek = DayIndexByEnglish[values.getOrNull(0)] ?: 0,
        hour = values.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 23) ?: 0,
        minute = values.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
    )
}

private val DayIndexByEnglish = mapOf(
    "Mon" to 0,
    "Tue" to 1,
    "Wed" to 2,
    "Thu" to 3,
    "Fri" to 4,
    "Sat" to 5,
    "Sun" to 6,
)
