package com.joon.ringout.presentation.records

import com.joon.ringout.domain.missionhistory.MissionDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal actual fun formatRecordsRingingRange(started: String?, stopped: String?): String =
    if (started != null && stopped == null) started else "${started ?: "--:--"} ~ ${stopped ?: "--:--"}"

@Suppress("UNUSED_PARAMETER")
internal actual fun formatRecordsTime(epochMillis: Long, completedDate: MissionDate): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return time.format(DateTimeFormatter.ofPattern("HH:mm"))
}
