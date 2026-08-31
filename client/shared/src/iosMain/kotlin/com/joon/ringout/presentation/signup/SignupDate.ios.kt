package com.joon.ringout.presentation.signup

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.localeWithLocaleIdentifier

internal actual fun currentAgreementDate(): String = NSDateFormatter().run {
    locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    dateFormat = "yyyy-MM-dd"
    stringFromDate(NSDate())
}
