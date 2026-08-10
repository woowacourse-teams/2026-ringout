package com.joon.ringout.presentation.mypage

import com.joon.ringout.domain.missionhistory.MissionYearMonth
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

internal actual fun currentMissionYearMonth(): MissionYearMonth {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy|MM"
    }
    val values = formatter.stringFromDate(NSDate()).split("|")
    return MissionYearMonth(
        year = values.getOrNull(0)?.toIntOrNull() ?: 1970,
        month = values.getOrNull(1)?.toIntOrNull() ?: 1,
    )
}
