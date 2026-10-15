package com.joon.ringout.presentation.destination

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class DestinationCurrentLocationCache(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var cachedLocation: CachedLocation? = null

    fun update(latitude: Double, longitude: Double) {
        val selection = DestinationSelection(
            name = SelectedDestinationFallbackName,
            address = ResolvingDestinationAddress,
            latitude = latitude,
            longitude = longitude,
        )
        if (!selection.isConfiguredDestination()) return

        cachedLocation = CachedLocation(selection, timeSource.markNow())
    }

    fun initialLocation(): DestinationSelection? {
        val cached = cachedLocation ?: return null
        val age = cached.receivedAt.elapsedNow()
        if (age !in Duration.ZERO..DestinationLocationCacheMaxAgeMillis.milliseconds) {
            clear()
            return null
        }
        return cached.selection
    }

    fun clear() {
        cachedLocation = null
    }

    private data class CachedLocation(
        val selection: DestinationSelection,
        val receivedAt: TimeMark,
    )
}
