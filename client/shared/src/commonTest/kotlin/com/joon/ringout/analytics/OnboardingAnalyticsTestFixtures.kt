package com.joon.ringout.analytics

internal class OnboardingAnalyticsFixture {
    val events = mutableListOf<AnalyticsEvent>()
    val tracker = AnalyticsTracker { events += it }
    val store = InMemoryProductAnalyticsUsageStore()
    val recorder = DefaultProductAnalyticsRecorder(tracker, store)
}

internal class InMemoryProductAnalyticsUsageStore : ProductAnalyticsUsageStore {
    private val onboardingClaims = mutableSetOf<AnalyticsEventName>()
    private val destinationClaims = mutableSetOf<String>()
    override fun claimOnboardingEvent(eventName: AnalyticsEventName): Boolean = onboardingClaims.add(eventName)
    override fun claimDestinationCreation(destinationKey: String): Long? =
        if (destinationClaims.add(destinationKey)) destinationClaims.size.toLong() else null
}
