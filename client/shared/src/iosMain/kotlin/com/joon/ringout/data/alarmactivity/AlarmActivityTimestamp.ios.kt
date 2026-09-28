package com.joon.ringout.data.alarmactivity

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.timeIntervalSince1970

internal actual fun currentAlarmActivityTimestamp(): AlarmActivityTimestamp {
    val now = NSDate()
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd"
    }
    return AlarmActivityTimestamp((now.timeIntervalSince1970 * 1_000).toLong(), formatter.stringFromDate(now))
}
