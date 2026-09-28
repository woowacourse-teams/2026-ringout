package com.joon.ringout.presentation.records

import com.joon.ringout.domain.missionhistory.MissionDate
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

internal actual fun formatRecordsTime(epochMillis: Long, completedDate: MissionDate): String {
    val time = NSDate(timeIntervalSinceReferenceDate = epochMillis / 1_000.0 - 978_307_200.0)
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd"
    }
    val sameDay = formatter.stringFromDate(time) == completedDate.iso8601
    formatter.dateFormat = if (sameDay) "HH:mm" else "M/d HH:mm"
    return formatter.stringFromDate(time)
}
