package com.joon.ringout.presentation.destination

import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationLocationPolicyTest {
    @Test
    fun freshPreciseLocationCanBeUsedWithoutRefinement() {
        assertEquals(
            DestinationLocationDecision.UseFinal,
            decideDestinationLocation(
                quality = DestinationLocationQuality(
                    ageMillis = DestinationLocationFreshMaxAgeMillis,
                    accuracyMeters = DestinationLocationFineTargetAccuracyMeters,
                ),
                granularity = DestinationLocationGranularity.Fine,
            ),
        )
    }

    @Test
    fun usableFineCacheIsShownBeforeRefinement() {
        assertEquals(
            DestinationLocationDecision.UseAndRefine,
            decideDestinationLocation(
                quality = DestinationLocationQuality(
                    ageMillis = DestinationLocationCacheMaxAgeMillis,
                    accuracyMeters = DestinationLocationFineInitialAccuracyMeters,
                ),
                granularity = DestinationLocationGranularity.Fine,
            ),
        )
    }

    @Test
    fun staleOrInaccurateFineCacheIsRejected() {
        assertEquals(
            DestinationLocationDecision.Reject,
            decideDestinationLocation(
                quality = DestinationLocationQuality(
                    ageMillis = DestinationLocationCacheMaxAgeMillis + 1,
                    accuracyMeters = DestinationLocationFineInitialAccuracyMeters,
                ),
                granularity = DestinationLocationGranularity.Fine,
            ),
        )
        assertEquals(
            DestinationLocationDecision.Reject,
            decideDestinationLocation(
                quality = DestinationLocationQuality(
                    ageMillis = 0L,
                    accuracyMeters = DestinationLocationFineInitialAccuracyMeters + 1f,
                ),
                granularity = DestinationLocationGranularity.Fine,
            ),
        )
    }

    @Test
    fun coarseLocationUsesItsOwnAccuracyLimitWithoutRefinement() {
        assertEquals(
            DestinationLocationDecision.UseFinal,
            decideDestinationLocation(
                quality = DestinationLocationQuality(
                    ageMillis = DestinationLocationCacheMaxAgeMillis,
                    accuracyMeters = DestinationLocationCoarseAccuracyMeters,
                ),
                granularity = DestinationLocationGranularity.Coarse,
            ),
        )
        assertEquals(
            DestinationLocationDecision.Reject,
            decideDestinationLocation(
                quality = DestinationLocationQuality(
                    ageMillis = 0L,
                    accuracyMeters = DestinationLocationCoarseAccuracyMeters + 1f,
                ),
                granularity = DestinationLocationGranularity.Coarse,
            ),
        )
    }

    @Test
    fun invalidAccuracyAndExcessiveClockSkewAreRejected() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f).forEach { accuracy ->
            assertEquals(
                DestinationLocationDecision.Reject,
                decideDestinationLocation(
                    quality = DestinationLocationQuality(
                        ageMillis = 0L,
                        accuracyMeters = accuracy,
                    ),
                    granularity = DestinationLocationGranularity.Fine,
                ),
            )
        }
        assertEquals(
            DestinationLocationDecision.Reject,
            decideDestinationLocation(
                quality = DestinationLocationQuality(
                    ageMillis = -DestinationLocationClockSkewMillis - 1,
                    accuracyMeters = DestinationLocationFineTargetAccuracyMeters,
                ),
                granularity = DestinationLocationGranularity.Fine,
            ),
        )
    }

    @Test
    fun freshRefinementMustNotBeSignificantlyLessAccurateThanCache() {
        val cached = DestinationLocationQuality(
            ageMillis = 31_000L,
            accuracyMeters = 10f,
        )

        assertEquals(
            false,
            shouldUseRefinedDestinationLocation(
                fallback = cached,
                refined = DestinationLocationQuality(
                    ageMillis = 0L,
                    accuracyMeters = 199f,
                ),
            ),
        )
        assertEquals(
            true,
            shouldUseRefinedDestinationLocation(
                fallback = cached,
                refined = DestinationLocationQuality(
                    ageMillis = 0L,
                    accuracyMeters = 40f,
                ),
            ),
        )
    }
}
