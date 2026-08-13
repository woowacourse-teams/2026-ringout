package com.joon.ringout.presentation.activemission

import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.MaximumAcceptedLocationAccuracyMeters
import com.joon.ringout.alarm.MaximumTrackingLocationAgeMillis
import com.joon.ringout.alarm.MissionLocationAccuracyState
import com.joon.ringout.alarm.MissionLocationAuthorizationState
import com.joon.ringout.alarm.MissionLocationServicesState
import com.joon.ringout.alarm.MissionLocationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun exposesReducedAccuracyAndUnavailableTrackingStatesToMissionUi() {
        assertEquals(
            "정확한 위치가 꺼져 있어 도착 판정이 늦어질 수 있어요.",
            missionLocationStatusMessage(
                locationState(accuracy = MissionLocationAccuracyState.REDUCED),
            ),
        )
        assertEquals(
            "위치 서비스가 꺼져 있어 도착 여부를 확인할 수 없어요.",
            missionLocationStatusMessage(
                locationState(services = MissionLocationServicesState.DISABLED),
            ),
        )
        assertNull(missionLocationStatusMessage(locationState()))
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

    private fun locationState(
        services: MissionLocationServicesState = MissionLocationServicesState.ENABLED,
        authorization: MissionLocationAuthorizationState =
            MissionLocationAuthorizationState.ALWAYS,
        accuracy: MissionLocationAccuracyState = MissionLocationAccuracyState.FULL,
    ) = MissionLocationState(
        services = services,
        authorization = authorization,
        accuracy = accuracy,
        isTracking = true,
    )

    private companion object {
        const val NowEpochMillis = 1_800_000_000_000L
    }
}
