package com.joon.ringout.alarm

internal data class TrackingLocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtEpochMillis: Long,
    val elapsedRealtimeNanos: Long,
)

internal enum class TrackingLocationDecision {
    Accept,
    InvalidCoordinates,
    InvalidAccuracy,
    InvalidElapsedRealtime,
    Stale,
    FromFuture,
    BeforeMissionStart,
    DuplicateOrOutOfOrder,
    LowerQualityThanRecentFix,
}

internal data class TrackingLocationHandling(
    val decision: TrackingLocationDecision,
    val shouldForwardToCoordinator: Boolean,
    val shouldReplaceLastAcceptedFix: Boolean,
)

internal fun evaluateTrackingLocationHandling(
    previous: TrackingLocationFix?,
    candidate: TrackingLocationFix,
    nowElapsedRealtimeNanos: Long,
    mission: ActiveAlarmMission,
): TrackingLocationHandling {
    val decision = evaluateTrackingLocationFix(
        previous = previous,
        candidate = candidate,
        nowElapsedRealtimeNanos = nowElapsedRealtimeNanos,
        missionStartedAtEpochMillis = mission.startedAtEpochMillis,
    )
    val isArrivalEvidenceRejectedOnlyByComparison =
        decision.isPreviousFixComparisonRejection() &&
            mission.hasReachedDestination(
                latitude = candidate.latitude,
                longitude = candidate.longitude,
                accuracyMeters = candidate.accuracyMeters,
            )
    return TrackingLocationHandling(
        decision = decision,
        shouldForwardToCoordinator =
            decision == TrackingLocationDecision.Accept ||
                isArrivalEvidenceRejectedOnlyByComparison,
        shouldReplaceLastAcceptedFix = decision == TrackingLocationDecision.Accept,
    )
}

internal fun evaluateTrackingLocationFix(
    previous: TrackingLocationFix?,
    candidate: TrackingLocationFix,
    nowElapsedRealtimeNanos: Long,
    missionStartedAtEpochMillis: Long,
): TrackingLocationDecision {
    if (
        !candidate.latitude.isFinite() || candidate.latitude !in -90.0..90.0 ||
        !candidate.longitude.isFinite() || candidate.longitude !in -180.0..180.0
    ) {
        return TrackingLocationDecision.InvalidCoordinates
    }
    if (
        !candidate.accuracyMeters.isFinite() ||
        candidate.accuracyMeters !in 0f..MaximumTrackingAccuracyMeters
    ) {
        return TrackingLocationDecision.InvalidAccuracy
    }
    if (candidate.elapsedRealtimeNanos <= 0L) {
        return TrackingLocationDecision.InvalidElapsedRealtime
    }

    val candidateAgeNanos = nowElapsedRealtimeNanos - candidate.elapsedRealtimeNanos
    if (candidateAgeNanos > MaximumTrackingLocationAgeNanos) {
        return TrackingLocationDecision.Stale
    }
    if (candidateAgeNanos < -TrackingClockSkewToleranceNanos) {
        return TrackingLocationDecision.FromFuture
    }
    if (candidate.capturedAtEpochMillis < missionStartedAtEpochMillis) {
        return TrackingLocationDecision.BeforeMissionStart
    }

    if (previous == null) return TrackingLocationDecision.Accept
    if (candidate.elapsedRealtimeNanos < previous.elapsedRealtimeNanos) {
        return TrackingLocationDecision.DuplicateOrOutOfOrder
    }
    if (
        candidate.elapsedRealtimeNanos == previous.elapsedRealtimeNanos &&
        candidate.accuracyMeters >= previous.accuracyMeters
    ) {
        return TrackingLocationDecision.DuplicateOrOutOfOrder
    }

    val previousAgeNanos = nowElapsedRealtimeNanos - previous.elapsedRealtimeNanos
    val accuracyDegradationMeters = candidate.accuracyMeters - previous.accuracyMeters
    if (
        previousAgeNanos <= RecentTrackingFixRetentionNanos &&
        accuracyDegradationMeters > MaximumAccuracyDegradationMeters
    ) {
        return TrackingLocationDecision.LowerQualityThanRecentFix
    }

    return TrackingLocationDecision.Accept
}

private fun TrackingLocationDecision.isPreviousFixComparisonRejection(): Boolean =
    this == TrackingLocationDecision.DuplicateOrOutOfOrder ||
        this == TrackingLocationDecision.LowerQualityThanRecentFix

private val MaximumTrackingAccuracyMeters = MaximumAcceptedLocationAccuracyMeters.toFloat()
private const val MaximumAccuracyDegradationMeters = 25f
private const val NanosPerSecond = 1_000_000_000L
internal const val MaximumTrackingLocationAgeMillis = 30_000L
private const val NanosPerMillisecond = 1_000_000L
private const val MaximumTrackingLocationAgeNanos =
    MaximumTrackingLocationAgeMillis * NanosPerMillisecond
private const val RecentTrackingFixRetentionNanos = 15L * NanosPerSecond
private const val TrackingClockSkewToleranceNanos = NanosPerSecond
