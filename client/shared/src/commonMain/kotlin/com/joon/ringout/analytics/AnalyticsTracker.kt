package com.joon.ringout.analytics

internal fun interface AnalyticsTracker {
    fun log(event: AnalyticsEvent)
}
