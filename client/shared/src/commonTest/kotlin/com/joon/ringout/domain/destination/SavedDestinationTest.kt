package com.joon.ringout.domain.destination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SavedDestinationTest {
    @Test
    fun acceptsLabelsLongerThanTheUiInputLimit() {
        val destination = SavedDestination(
            label = "열한글자보다긴목적지라벨도보존",
            latitude = 37.5665,
            longitude = 126.978,
        )

        assertEquals("열한글자보다긴목적지라벨도보존", destination.label)
    }

    @Test
    fun rejectsNegativeIdsAndBlankLabels() {
        assertFailsWith<IllegalArgumentException> {
            destination(id = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            destination(label = "   ")
        }
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeCoordinates() {
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, -90.0001, 90.0001).forEach { latitude ->
            assertFailsWith<IllegalArgumentException> {
                destination(latitude = latitude)
            }
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -180.0001, 180.0001).forEach { longitude ->
            assertFailsWith<IllegalArgumentException> {
                destination(longitude = longitude)
            }
        }
    }

    private fun destination(
        id: Long = 0,
        label: String = "회사",
        latitude: Double = 37.5665,
        longitude: Double = 126.978,
    ) = SavedDestination(
        id = id,
        label = label,
        latitude = latitude,
        longitude = longitude,
    )
}
