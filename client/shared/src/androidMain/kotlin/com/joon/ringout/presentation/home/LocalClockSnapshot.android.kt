package com.joon.ringout.presentation.home

import java.time.LocalDateTime

internal actual fun currentLocalClockSnapshot(): LocalClockSnapshot {
    val now = LocalDateTime.now()
    return LocalClockSnapshot(
        dayOfWeek = now.dayOfWeek.value - 1,
        hour = now.hour,
        minute = now.minute,
    )
}
