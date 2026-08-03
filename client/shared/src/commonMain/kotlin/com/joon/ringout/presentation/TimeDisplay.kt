package com.joon.ringout.presentation

internal data class TwelveHourDisplay(
    val period: String,
    val time: String,
)

internal fun String.toTwelveHourDisplay(): TwelveHourDisplay {
    val rawHour = substringBefore(":").toIntOrNull() ?: 0
    val minute = substringAfter(":", "00").toIntOrNull()?.coerceIn(0, 59) ?: 0
    val hour24 = ((rawHour % 24) + 24) % 24
    val hour12 = (hour24 % 12).takeIf { it != 0 } ?: 12

    return TwelveHourDisplay(
        period = if (hour24 < 12) "오전" else "오후",
        time = "${hour12.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
    )
}
