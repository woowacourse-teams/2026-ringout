package com.joon.ringout.presentation.activemission

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
}
