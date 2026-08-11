package com.joon.ringout.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackingLocationQualityPolicyTest {
    @Test
    fun acceptsFirstFreshAccurateFix() {
        assertEquals(
            TrackingLocationDecision.Accept,
            evaluate(candidate = fix()),
        )
    }

    @Test
    fun rejectsInvalidCoordinatesAndAccuracy() {
        assertEquals(
            TrackingLocationDecision.InvalidCoordinates,
            evaluate(candidate = fix(latitude = Double.NaN)),
        )
        assertEquals(
            TrackingLocationDecision.InvalidAccuracy,
            evaluate(candidate = fix(accuracyMeters = Float.POSITIVE_INFINITY)),
        )
        assertEquals(
            TrackingLocationDecision.InvalidAccuracy,
            evaluate(
                candidate = fix(
                    accuracyMeters = MaximumAcceptedLocationAccuracyMeters.toFloat() + 1f,
                ),
            ),
        )
    }

    @Test
    fun rejectsStaleAndFutureFixesUsingMonotonicTime() {
        assertEquals(
            TrackingLocationDecision.Stale,
            evaluate(candidate = fix(elapsedRealtimeNanos = NowNanos - 31L * NanosPerSecond)),
        )
        assertEquals(
            TrackingLocationDecision.FromFuture,
            evaluate(candidate = fix(elapsedRealtimeNanos = NowNanos + 2L * NanosPerSecond)),
        )
    }

    @Test
    fun rejectsFixCapturedBeforeMissionStarted() {
        assertEquals(
            TrackingLocationDecision.BeforeMissionStart,
            evaluate(candidate = fix(capturedAtEpochMillis = MissionStartedAtMillis - 1L)),
        )
    }

    @Test
    fun rejectsOutOfOrderFixEvenWhenItIsMoreAccurate() {
        assertEquals(
            TrackingLocationDecision.DuplicateOrOutOfOrder,
            evaluate(
                previous = fix(
                    accuracyMeters = 20f,
                    elapsedRealtimeNanos = NowNanos - NanosPerSecond,
                ),
                candidate = fix(
                    accuracyMeters = 5f,
                    elapsedRealtimeNanos = NowNanos - 2L * NanosPerSecond,
                ),
            ),
        )
    }

    @Test
    fun acceptsAccuracyImprovementWithoutRequiringMovement() {
        assertEquals(
            TrackingLocationDecision.Accept,
            evaluate(
                previous = fix(
                    latitude = 37.5665,
                    longitude = 126.9780,
                    accuracyMeters = 30f,
                    elapsedRealtimeNanos = NowNanos - 2L * NanosPerSecond,
                ),
                candidate = fix(
                    latitude = 37.5665,
                    longitude = 126.9780,
                    accuracyMeters = 8f,
                    elapsedRealtimeNanos = NowNanos - NanosPerSecond,
                ),
            ),
        )
    }

    @Test
    fun keepsRecentAccurateFixInsteadOfLargeAccuracyRegression() {
        assertEquals(
            TrackingLocationDecision.LowerQualityThanRecentFix,
            evaluate(
                previous = fix(
                    accuracyMeters = 5f,
                    elapsedRealtimeNanos = NowNanos - 2L * NanosPerSecond,
                ),
                candidate = fix(
                    accuracyMeters = 50f,
                    elapsedRealtimeNanos = NowNanos - NanosPerSecond,
                ),
            ),
        )
    }

    @Test
    fun acceptsNewerUsableFixAfterPreviousFixBecomesStale() {
        assertEquals(
            TrackingLocationDecision.Accept,
            evaluate(
                previous = fix(
                    accuracyMeters = 5f,
                    elapsedRealtimeNanos = NowNanos - 16L * NanosPerSecond,
                ),
                candidate = fix(
                    accuracyMeters = 50f,
                    elapsedRealtimeNanos = NowNanos - NanosPerSecond,
                ),
            ),
        )
    }

    @Test
    fun forwardsLessAccurateArrivalEvidenceWithoutReplacingRecentOutsideFix() {
        val mission = mission()
        val handling = evaluateTrackingLocationHandling(
            previous = fix(
                latitude = 37.5670,
                accuracyMeters = 5f,
                elapsedRealtimeNanos = NowNanos - 2L * NanosPerSecond,
            ),
            candidate = fix(
                latitude = mission.destinationLatitude,
                longitude = mission.destinationLongitude,
                accuracyMeters = 40f,
                elapsedRealtimeNanos = NowNanos - NanosPerSecond,
            ),
            nowElapsedRealtimeNanos = NowNanos,
            mission = mission,
        )

        assertEquals(TrackingLocationDecision.LowerQualityThanRecentFix, handling.decision)
        assertTrue(handling.shouldForwardToCoordinator)
        assertFalse(handling.shouldReplaceLastAcceptedFix)
    }

    @Test
    fun forwardsOutOfOrderArrivalEvidenceWithoutMovingLatestFixBackward() {
        val mission = mission()
        val handling = evaluateTrackingLocationHandling(
            previous = fix(
                latitude = 37.5670,
                accuracyMeters = 5f,
                elapsedRealtimeNanos = NowNanos - NanosPerSecond,
            ),
            candidate = fix(
                latitude = mission.destinationLatitude,
                longitude = mission.destinationLongitude,
                accuracyMeters = 40f,
                elapsedRealtimeNanos = NowNanos - 2L * NanosPerSecond,
            ),
            nowElapsedRealtimeNanos = NowNanos,
            mission = mission,
        )

        assertEquals(TrackingLocationDecision.DuplicateOrOutOfOrder, handling.decision)
        assertTrue(handling.shouldForwardToCoordinator)
        assertFalse(handling.shouldReplaceLastAcceptedFix)
    }

    @Test
    fun doesNotForwardStaleFixEvenWhenItsCoordinatesAreAtDestination() {
        val mission = mission()
        val handling = evaluateTrackingLocationHandling(
            previous = null,
            candidate = fix(
                latitude = mission.destinationLatitude,
                longitude = mission.destinationLongitude,
                accuracyMeters = 5f,
                elapsedRealtimeNanos = NowNanos - 31L * NanosPerSecond,
            ),
            nowElapsedRealtimeNanos = NowNanos,
            mission = mission,
        )

        assertEquals(TrackingLocationDecision.Stale, handling.decision)
        assertFalse(handling.shouldForwardToCoordinator)
        assertFalse(handling.shouldReplaceLastAcceptedFix)
    }

    private fun evaluate(
        previous: TrackingLocationFix? = null,
        candidate: TrackingLocationFix,
    ): TrackingLocationDecision = evaluateTrackingLocationFix(
        previous = previous,
        candidate = candidate,
        nowElapsedRealtimeNanos = NowNanos,
        missionStartedAtEpochMillis = MissionStartedAtMillis,
    )

    private fun fix(
        latitude: Double = 37.5665,
        longitude: Double = 126.9780,
        accuracyMeters: Float = 10f,
        capturedAtEpochMillis: Long = MissionStartedAtMillis + 1_000L,
        elapsedRealtimeNanos: Long = NowNanos - NanosPerSecond,
    ): TrackingLocationFix = TrackingLocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        capturedAtEpochMillis = capturedAtEpochMillis,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
    )

    private fun mission(): ActiveAlarmMission = ActiveAlarmMission(
        alarmId = "alarm-1",
        destinationName = "목적지",
        limitMinutes = 10,
        expiresAtEpochMillis = MissionStartedAtMillis + 600_000L,
        startedAtEpochMillis = MissionStartedAtMillis,
        destinationLatitude = 37.5665,
        destinationLongitude = 126.9780,
    )

    private companion object {
        const val NanosPerSecond = 1_000_000_000L
        const val NowNanos = 100L * NanosPerSecond
        const val MissionStartedAtMillis = 1_000_000L
    }
}
