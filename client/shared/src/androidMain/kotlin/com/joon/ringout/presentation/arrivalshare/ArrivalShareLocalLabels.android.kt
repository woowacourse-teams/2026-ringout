package com.joon.ringout.presentation.arrivalshare
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
internal actual fun arrivalShareLocalLabels(epochMillis: Long): Pair<String, String> {
    val local = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return local.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) to local.format(DateTimeFormatter.ofPattern("HH:mm"))
}
