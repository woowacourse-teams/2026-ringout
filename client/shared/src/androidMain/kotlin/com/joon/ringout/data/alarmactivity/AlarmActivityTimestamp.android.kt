package com.joon.ringout.data.alarmactivity

import java.time.Instant
import java.time.ZoneId

internal actual fun currentAlarmActivityTimestamp(): AlarmActivityTimestamp {
    val now = Instant.now()
    return AlarmActivityTimestamp(now.toEpochMilli(), now.atZone(ZoneId.systemDefault()).toLocalDate().toString())
}
