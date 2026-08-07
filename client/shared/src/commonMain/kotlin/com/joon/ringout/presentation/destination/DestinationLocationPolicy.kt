package com.joon.ringout.presentation.destination

internal enum class DestinationLocationGranularity {
    Fine,
    Coarse,
}

internal enum class DestinationLocationDecision {
    Reject,
    UseFinal,
    UseAndRefine,
}

internal data class DestinationLocationQuality(
    val ageMillis: Long,
    val accuracyMeters: Float,
)

internal fun decideDestinationLocation(
    quality: DestinationLocationQuality,
    granularity: DestinationLocationGranularity,
): DestinationLocationDecision {
    if (
        quality.ageMillis !in -DestinationLocationClockSkewMillis..DestinationLocationCacheMaxAgeMillis ||
        !quality.accuracyMeters.isFinite() ||
        quality.accuracyMeters < 0f
    ) {
        return DestinationLocationDecision.Reject
    }

    return when (granularity) {
        DestinationLocationGranularity.Fine -> when {
            quality.accuracyMeters > DestinationLocationFineInitialAccuracyMeters ->
                DestinationLocationDecision.Reject

            quality.ageMillis <= DestinationLocationFreshMaxAgeMillis &&
                quality.accuracyMeters <= DestinationLocationFineTargetAccuracyMeters ->
                DestinationLocationDecision.UseFinal

            else -> DestinationLocationDecision.UseAndRefine
        }

        DestinationLocationGranularity.Coarse ->
            if (quality.accuracyMeters <= DestinationLocationCoarseAccuracyMeters) {
                DestinationLocationDecision.UseFinal
            } else {
                DestinationLocationDecision.Reject
            }
    }
}

internal fun shouldUseRefinedDestinationLocation(
    fallback: DestinationLocationQuality,
    refined: DestinationLocationQuality,
): Boolean {
    val maximumAcceptedAccuracy = maxOf(
        DestinationLocationFineTargetAccuracyMeters,
        fallback.accuracyMeters * DestinationLocationRefinementAccuracyMultiplier,
    )
    return refined.ageMillis < fallback.ageMillis &&
        refined.accuracyMeters <= maximumAcceptedAccuracy
}

internal const val DestinationLocationCacheMaxAgeMillis = 3 * 60_000L
internal const val DestinationLocationFreshMaxAgeMillis = 30_000L
internal const val DestinationLocationClockSkewMillis = 5_000L
internal const val DestinationLocationFineInitialAccuracyMeters = 200f
internal const val DestinationLocationFineTargetAccuracyMeters = 50f
internal const val DestinationLocationCoarseAccuracyMeters = 3_000f
private const val DestinationLocationRefinementAccuracyMultiplier = 1.5f
