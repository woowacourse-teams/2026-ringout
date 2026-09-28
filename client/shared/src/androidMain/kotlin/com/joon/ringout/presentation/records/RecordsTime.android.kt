package com.joon.ringout.presentation.records

import com.joon.ringout.domain.missionhistory.MissionDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal actual fun formatRecordsRingingRange(started: String?, stopped: String?): String =
    if (started != null && stopped == null) started else "${started ?: "--:--"} ~ ${stopped ?: "--:--"}"

internal actual fun formatRecordsTime(epochMillis: Long, completedDate: MissionDate): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val pattern = if (time.toLocalDate().toString() == completedDate.iso8601) "HH:mm" else "M/d HH:mm"
    return time.format(DateTimeFormatter.ofPattern(pattern))
}
