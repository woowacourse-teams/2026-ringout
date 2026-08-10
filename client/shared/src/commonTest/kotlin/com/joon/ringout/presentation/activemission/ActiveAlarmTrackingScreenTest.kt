package com.joon.ringout.presentation.activemission

import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.MaximumAcceptedLocationAccuracyMeters
import com.joon.ringout.alarm.MaximumTrackingLocationAgeMillis
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActiveAlarmTrackingScreenTest {
    @Test
    fun acceptsValidMapCoordinate() {
        assertTrue(
            isValidMapCoordinate(
                latitude = 37.2875205998,
                longitude = 127.0146478075,
            ),
        )
    }

    @Test
    fun rejectsMissingDestinationCoordinate() {
        assertFalse(
            isValidMapCoordinate(
                latitude = Double.NaN,
                longitude = 127.0146478075,
            ),
        )
    }

    @Test
    fun rejectsOutOfRangeMapCoordinate() {
        assertFalse(
            isValidMapCoordinate(
                latitude = 91.0,
                longitude = 127.0146478075,
            ),
        )
        assertFalse(
            isValidMapCoordinate(
                latitude = 37.2875205998,
                longitude = 181.0,
            ),
        )
    }

    @Test
    fun acceptsFreshAccurateCurrentLocationForMapMarker() {
        assertTrue(
            isUsableActiveAlarmMapLocation(
                location = location(
                    accuracyMeters = MaximumAcceptedLocationAccuracyMeters.toFloat(),
                    capturedAtEpochMillis = NowEpochMillis - MaximumTrackingLocationAgeMillis,
                ),
                nowEpochMillis = NowEpochMillis,
            ),
        )
    }

    @Test
    fun hidesStaleInaccurateFutureAndInvalidCurrentLocations() {
        assertFalse(
            isUsableActiveAlarmMapLocation(
                location = location(
                    capturedAtEpochMillis =
                        NowEpochMillis - MaximumTrackingLocationAgeMillis - 1L,
                ),
                nowEpochMillis = NowEpochMillis,
            ),
        )
        assertFalse(
            isUsableActiveAlarmMapLocation(
                location = location(
                    accuracyMeters = MaximumAcceptedLocationAccuracyMeters.toFloat() + 1f,
                ),
                nowEpochMillis = NowEpochMillis,
            ),
        )
        assertFalse(
            isUsableActiveAlarmMapLocation(
                location = location(capturedAtEpochMillis = NowEpochMillis + 1L),
                nowEpochMillis = NowEpochMillis,
            ),
        )
        assertFalse(
            isUsableActiveAlarmMapLocation(
                location = location(latitude = Double.NaN),
                nowEpochMillis = NowEpochMillis,
            ),
        )
    }

    private fun location(
        latitude: Double = 37.2875205998,
        longitude: Double = 127.0146478075,
        accuracyMeters: Float = 10f,
        capturedAtEpochMillis: Long = NowEpochMillis - 1_000L,
    ): ActiveAlarmMissionLocation = ActiveAlarmMissionLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        capturedAtEpochMillis = capturedAtEpochMillis,
    )

    private companion object {
        const val NowEpochMillis = 1_800_000_000_000L
    }
}
