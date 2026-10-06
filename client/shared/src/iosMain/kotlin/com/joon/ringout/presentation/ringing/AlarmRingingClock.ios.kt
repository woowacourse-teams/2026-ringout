package com.joon.ringout.presentation.ringing

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

internal actual fun currentAlarmRingingClock(): AlarmRingingClockUiState {
    val now = NSDate()
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "ko_KR")
        dateFormat = "HH:mm"
    }
    val time = formatter.stringFromDate(now)
    formatter.dateFormat = "yyyy년 M월 d일 EEEE"
    return AlarmRingingClockUiState(time = time, dateText = formatter.stringFromDate(now))
}
