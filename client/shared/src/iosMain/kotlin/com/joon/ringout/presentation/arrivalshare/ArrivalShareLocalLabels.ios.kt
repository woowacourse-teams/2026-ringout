package com.joon.ringout.presentation.arrivalshare
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970
internal actual fun arrivalShareLocalLabels(epochMillis: Long): Pair<String, String> {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
    fun label(pattern: String) = NSDateFormatter().apply {
        locale = NSLocale("en_US_POSIX")
        dateFormat = pattern
    }.stringFromDate(date)
    return label("yyyy.MM.dd") to label("HH:mm")
}
