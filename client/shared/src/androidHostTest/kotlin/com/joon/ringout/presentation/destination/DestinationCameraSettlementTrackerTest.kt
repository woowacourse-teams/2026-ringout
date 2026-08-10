package com.joon.ringout.presentation.destination

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DestinationCameraSettlementTrackerTest {
    @Test
    fun firstSettlementIsDispatchedEvenWithoutObservedMoveStart() {
        val tracker = DestinationCameraSettlementTracker()

        val generation = tracker.registerSettlement(
            latitude = CurrentLatitude,
            longitude = CurrentLongitude,
        )

        assertNotNull(generation)
        assertTrue(
            tracker.isCurrentSettlement(
                generation = generation,
                latitude = CurrentLatitude,
                longitude = CurrentLongitude,
            ),
        )
    }

    @Test
    fun duplicateIdleEventDoesNotDispatchTheSameSettlementTwice() {
        val tracker = DestinationCameraSettlementTracker()

        tracker.registerSettlement(
            latitude = CurrentLatitude,
            longitude = CurrentLongitude,
        )

        assertNull(
            tracker.registerSettlement(
                latitude = CurrentLatitude,
                longitude = CurrentLongitude,
            ),
        )
    }

    @Test
    fun invalidationRejectsAnOldAddressEvenWhenTheNextTargetHasTheSameCoordinates() {
        val tracker = DestinationCameraSettlementTracker()
        val previousGeneration = tracker.registerSettlement(
            latitude = CurrentLatitude,
            longitude = CurrentLongitude,
        )
        assertNotNull(previousGeneration)

        tracker.invalidateSettlement()

        assertFalse(
            tracker.isCurrentSettlement(
                generation = previousGeneration,
                latitude = CurrentLatitude,
                longitude = CurrentLongitude,
            ),
        )
        assertNotNull(
            tracker.registerSettlement(
                latitude = CurrentLatitude,
                longitude = CurrentLongitude,
            ),
        )
    }

    @Test
    fun newMovementInvalidatesThePreviousAddressResolution() {
        val tracker = DestinationCameraSettlementTracker()
        val previousGeneration = tracker.registerSettlement(
            latitude = DefaultDestinationSelection.latitude,
            longitude = DefaultDestinationSelection.longitude,
        )
        assertNotNull(previousGeneration)

        tracker.invalidateSettlement()
        val currentGeneration = tracker.registerSettlement(
            latitude = CurrentLatitude,
            longitude = CurrentLongitude,
        )
        assertNotNull(currentGeneration)

        assertFalse(
            tracker.isCurrentSettlement(
                generation = previousGeneration,
                latitude = DefaultDestinationSelection.latitude,
                longitude = DefaultDestinationSelection.longitude,
            ),
        )
        assertTrue(
            tracker.isCurrentSettlement(
                generation = currentGeneration,
                latitude = CurrentLatitude,
                longitude = CurrentLongitude,
            ),
        )
    }

    private companion object {
        const val CurrentLatitude = 37.2875
        const val CurrentLongitude = 127.0146
    }
}
