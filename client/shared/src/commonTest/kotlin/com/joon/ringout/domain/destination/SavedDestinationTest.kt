package com.joon.ringout.domain.destination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SavedDestinationTest {
    @Test
    fun acceptsBlankAddressAndNamesLongerThanTheUiNicknameLimit() {
        val destination = SavedDestination(
            name = "열한글자보다긴목적지별명도보존",
            address = "",
            latitude = 37.5665,
            longitude = 126.978,
        )

        assertEquals("", destination.address)
        assertEquals("열한글자보다긴목적지별명도보존", destination.name)
    }

    @Test
    fun rejectsNegativeIdsAndBlankNames() {
        assertFailsWith<IllegalArgumentException> {
            destination(id = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            destination(name = "   ")
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
        name: String = "회사",
        latitude: Double = 37.5665,
        longitude: Double = 126.978,
    ) = SavedDestination(
        id = id,
        name = name,
        address = "서울특별시 중구 세종대로 110",
        latitude = latitude,
        longitude = longitude,
    )
}
