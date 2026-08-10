package com.joon.ringout.presentation.destination

import com.joon.ringout.domain.destination.SavedDestination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DestinationSavedEventTest {
    @Test
    fun appliesOnlyToTheVisibleDestinationRequestThatStartedTheSave() {
        val event = DestinationSavedEvent(
            eventId = 1L,
            requestId = 7L,
            destination = SavedDestination(
                id = 42L,
                name = "회사",
                address = "서울특별시 중구 세종대로 110",
                latitude = 37.5665,
                longitude = 126.978,
            ),
        )

        assertTrue(
            event.belongsToDestinationRequest(
                currentRequestId = 7L,
                isDestinationScreenVisible = true,
            ),
        )
        assertFalse(
            event.belongsToDestinationRequest(
                currentRequestId = 8L,
                isDestinationScreenVisible = true,
            ),
        )
        assertFalse(
            event.belongsToDestinationRequest(
                currentRequestId = 7L,
                isDestinationScreenVisible = false,
            ),
        )
    }
}
