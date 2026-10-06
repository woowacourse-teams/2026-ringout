package com.joon.ringout.analytics

internal fun roomTestRecorder(events: MutableList<AnalyticsEvent>): ProductAnalyticsRecorder =
    DefaultProductAnalyticsRecorder(AnalyticsTracker { events.add(it) }, object : ProductAnalyticsUsageStore {
        override fun claimOnboardingEvent(eventName: AnalyticsEventName) = true
        override fun claimDestinationCreation(destinationKey: String): Long = 1
    })
