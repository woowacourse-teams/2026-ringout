package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.SavedDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DestinationMapperTest {
    @Test
    fun roundTripsEveryStoredDestinationField() {
        val destination = SavedDestination(
            id = 42,
            name = "회사",
            address = "",
            latitude = 37.5665,
            longitude = 126.978,
        )

        assertEquals(destination, destination.toEntity().toDomain())
    }

    @Test
    fun rejectsInvalidPersistedDestinationsWhenMappingToDomain() {
        val valid = SavedDestinationEntity(
            id = 1,
            name = "회사",
            address = "서울특별시 중구 세종대로 110",
            latitude = 37.5665,
            longitude = 126.978,
        )

        listOf(
            valid.copy(id = -1),
            valid.copy(name = " "),
            valid.copy(latitude = Double.NaN),
            valid.copy(latitude = 90.0001),
            valid.copy(longitude = Double.POSITIVE_INFINITY),
            valid.copy(longitude = -180.0001),
        ).forEach { entity ->
            assertFailsWith<IllegalArgumentException> {
                entity.toDomain()
            }
        }
    }
}
