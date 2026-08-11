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
                latitude = 37.5667,
                longitude = 126.9780,
                accuracyMeters = 5f,
            ),
        )
    }

    @Test
    fun rejectsLocationOutsideDefaultDestinationRadius() {
        assertFalse(
            mission.hasReachedDestination(
                latitude = 37.5668,
                longitude = 126.9780,
                accuracyMeters = 5f,
            ),
        )
    }

    @Test
    fun acceptsLocationAccuracyIndependentlyFromArrivalRadius() {
        assertTrue(
            mission.hasReachedDestination(
                latitude = 37.5665,
                longitude = 126.9780,
                accuracyMeters = MaximumAcceptedLocationAccuracyMeters.toFloat(),
            ),
        )
    }

    @Test
    fun rejectsLocationAboveMaximumAcceptedAccuracy() {
        assertFalse(
            mission.hasReachedDestination(
                latitude = 37.5665,
                longitude = 126.9780,
                accuracyMeters = MaximumAcceptedLocationAccuracyMeters.toFloat() + 1f,
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
    fun acceptsFreshFixDuringTechnicalDeadlineVerificationGracePeriod() {
        assertTrue(
            mission.hasReachedDestinationByDeadline(
                latitude = 37.5665,
                longitude = 126.9780,
                accuracyMeters = 10f,
                capturedAtEpochMillis =
                    600_000L + DeadlineLocationGracePeriodMillis,
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

    @Test
    fun prefersCachedArrivalEvidenceOverNewerLocationOutsideDestination() {
        val cachedArrival = ActiveAlarmMissionLocation(
            latitude = 37.5665,
            longitude = 126.9780,
            accuracyMeters = 40f,
            capturedAtEpochMillis = 599_000L,
        )
        val verifierLocationOutsideDestination = ActiveAlarmMissionLocation(
            latitude = 37.5670,
            longitude = 126.9780,
            accuracyMeters = 5f,
            capturedAtEpochMillis = 600_500L,
        )

        val selected = mission.selectBestDeadlineLocation(
            listOf(cachedArrival, verifierLocationOutsideDestination),
        )

        assertTrue(selected === cachedArrival)
    }

    @Test
    fun selectsMoreAccurateArrivalEvidenceThenUsesNewestAsTieBreaker() {
        val olderAccurateArrival = ActiveAlarmMissionLocation(
            latitude = 37.5665,
            longitude = 126.9780,
            accuracyMeters = 5f,
            capturedAtEpochMillis = 598_000L,
        )
        val newerLessAccurateArrival = ActiveAlarmMissionLocation(
            latitude = 37.5665,
            longitude = 126.9780,
            accuracyMeters = 20f,
            capturedAtEpochMillis = 600_000L,
        )
        val newestEquallyAccurateArrival = olderAccurateArrival.copy(
            capturedAtEpochMillis = 600_500L,
        )

        val selected = mission.selectBestDeadlineLocation(
            listOf(
                olderAccurateArrival,
                newerLessAccurateArrival,
                newestEquallyAccurateArrival,
            ),
        )

        assertTrue(selected === newestEquallyAccurateArrival)
    }
}
