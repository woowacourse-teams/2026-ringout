package com.joon.ringout.presentation.records

import com.joon.ringout.domain.missionhistory.MissionDate
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.localeWithLocaleIdentifier

internal actual fun currentRecordsDate(): MissionDate = NSDateFormatter().run {
    locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    dateFormat = "yyyy-MM-dd"
    MissionDate.parse(stringFromDate(NSDate()))
}
