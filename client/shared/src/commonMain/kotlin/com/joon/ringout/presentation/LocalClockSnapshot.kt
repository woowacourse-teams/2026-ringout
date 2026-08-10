package com.joon.ringout.presentation

internal data class LocalClockSnapshot(
    val dayOfWeek: Int,
    val hour: Int,
    val minute: Int,
)

internal expect fun currentLocalClockSnapshot(): LocalClockSnapshot

internal fun LocalClockSnapshot.to24HourTimeString(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
