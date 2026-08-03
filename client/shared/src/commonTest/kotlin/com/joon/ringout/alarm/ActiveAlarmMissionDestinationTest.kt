package com.joon.ringout.alarm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActiveAlarmMissionDestinationTest {
    private val mission = ActiveAlarmMission(
        alarmId = "alarm-1",
        destinationName = "목적지",
        limitMinutes = 10,
        expiresAtEpochMillis = 600_000L,
        startedAtEpochMillis = 0L,
        destinationLatitude = 37.5665,
        destinationLongitude = 126.9780,
    )

    @Test
    fun acceptsLocationInsideDefaultDestinationRadius() {
        assertTrue(
            mission.hasReachedDestination(
                latitude = 37.56654,
                longitude = 126.9780,
                accuracyMeters = 5f,
            ),
        )
    }

    @Test
    fun rejectsLocationOutsideDefaultDestinationRadius() {
        assertFalse(
            mission.hasReachedDestination(
                latitude = 37.5666,
                longitude = 126.9780,
                accuracyMeters = 5f,
            ),
        )
    }

    @Test
    fun rejectsLocationWithInsufficientAccuracy() {
        assertFalse(
            mission.hasReachedDestination(
                latitude = 37.5665,
                longitude = 126.9780,
                accuracyMeters = 11f,
            ),
        )
    }

    @Test
    fun rejectsMissionWithoutDestinationCoordinates() {
        assertFalse(
            mission.copy(
                destinationLatitude = Double.NaN,
                destinationLongitude = Double.NaN,
            ).hasReachedDestination(
                latitude = 37.5665,
                longitude = 126.9780,
                accuracyMeters = 10f,
            ),
        )
    }

    @Test
    fun acceptsFreshArrivalEvidenceCapturedBeforeDeadline() {
        assertTrue(
            mission.hasReachedDestinationByDeadline(
                latitude = 37.5665,
                longitude = 126.9780,
                accuracyMeters = 10f,
                capturedAtEpochMillis = 599_000L,
            ),
        )
    }

    @Test
    fun rejectsStaleArrivalEvidenceAtDeadline() {
        assertFalse(
            mission.hasReachedDestinationByDeadline(
                latitude = 37.5665,
                longitude = 126.9780,
                accuracyMeters = 10f,
                capturedAtEpochMillis = 560_000L,
            ),
        )
    }

    @Test
    fun rejectsArrivalEvidenceCapturedAfterDeadline() {
        assertFalse(
            mission.hasReachedDestinationByDeadline(
                latitude = 37.5665,
                longitude = 126.9780,
                accuracyMeters = 10f,
                capturedAtEpochMillis = 600_001L,
            ),
        )
    }

    @Test
    fun rejectsArrivalEvidenceCapturedAfterLocationCheckTimeout() {
        assertFalse(
            mission.hasReachedDestinationByDeadline(
                latitude = 37.5665,
                longitude = 126.9780,
                accuracyMeters = 10f,
                capturedAtEpochMillis =
                    600_000L + DeadlineLocationAcquisitionTimeoutMillis + 1L,
            ),
        )
    }
}
