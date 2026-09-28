package com.joon.ringout.presentation.ringing

internal data class AlarmRingingClockUiState(
    val time: String,
    val dateText: String,
)

/** Read the time and date from the same device-clock instant. */
internal expect fun currentAlarmRingingClock(): AlarmRingingClockUiState
