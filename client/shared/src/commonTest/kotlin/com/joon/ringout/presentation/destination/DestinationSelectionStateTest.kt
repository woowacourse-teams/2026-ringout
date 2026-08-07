package com.joon.ringout.presentation.destination

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DestinationSelectionStateTest {
    @Test
    fun destinationWithNameAndValidCoordinatesIsConfigured() {
        assertTrue(
            DestinationSelection(
                name = "수원천",
                address = "",
                latitude = 37.2875,
                longitude = 127.0146,
            ).isConfiguredDestination(),
        )
    }

    @Test
    fun destinationWithoutNameIsNotConfigured() {
        assertFalse(
            DestinationSelection(
                name = "",
                address = "경기 수원시 팔달구",
                latitude = 37.2875,
                longitude = 127.0146,
            ).isConfiguredDestination(),
        )
    }

    @Test
    fun destinationOutsideCoordinateRangeIsNotConfigured() {
        assertFalse(
            DestinationSelection(
                name = "잘못된 위치",
                address = "",
                latitude = 91.0,
                longitude = 127.0146,
            ).isConfiguredDestination(),
        )
    }

    @Test
    fun destinationWithNonFiniteCoordinateIsNotConfigured() {
        assertFalse(
            DestinationSelection(
                name = "잘못된 위치",
                address = "",
                latitude = Double.NaN,
                longitude = 127.0146,
            ).isConfiguredDestination(),
        )
    }
}
