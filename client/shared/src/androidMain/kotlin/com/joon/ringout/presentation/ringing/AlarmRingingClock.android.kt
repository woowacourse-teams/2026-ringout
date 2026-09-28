package com.joon.ringout.presentation.ringing

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal actual fun currentAlarmRingingClock(): AlarmRingingClockUiState {
    val now = ZonedDateTime.now()
    return AlarmRingingClockUiState(
        time = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)),
        dateText = now.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN)),
    )
}
