package com.joon.ringout.presentation.destination

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DestinationPlaceMapperTest {
    @Test
    fun mapsRequestedPlaceFieldsToDestinationSelection() {
        val result = listOf(
            place(
                name = "수원역",
                address = "경기 수원시 팔달구 덕영대로 924",
                latitude = 37.2657,
                longitude = 127.0001,
            ),
        ).toDestinationSelections().single()

        assertEquals("수원역", result.name)
        assertEquals("경기 수원시 팔달구 덕영대로 924", result.address)
        assertEquals(37.2657, result.latitude)
        assertEquals(127.0001, result.longitude)
    }

    @Test
    fun skipsPlacesWithoutCoordinates() {
        val place = Place.builder()
            .setDisplayName("좌표 없는 장소")
            .setFormattedAddress("주소")
            .build()

        assertTrue(listOf(place).toDestinationSelections().isEmpty())
    }

    @Test
    fun fallsBackBetweenGooglePlaceNameAndAddress() {
        val addressOnly = place(
            name = "",
            address = "경기 수원시 팔달구 덕영대로 924",
            latitude = 37.2657,
            longitude = 127.0001,
        )
        val nameOnly = place(
            name = "수원역",
            address = "",
            latitude = 37.2658,
            longitude = 127.0002,
        )
        val blank = place(
            name = "",
            address = "",
            latitude = 37.2659,
            longitude = 127.0003,
        )

        val results = listOf(addressOnly, nameOnly, blank).toDestinationSelections()

        assertEquals(2, results.size)
        assertEquals(results[0].name, results[0].address)
        assertEquals(results[1].name, results[1].address)
    }

    @Test
    fun removesDuplicateCoordinates() {
        val results = listOf(
            place("첫 번째", "주소 1", 37.2657001, 127.0001001),
            place("두 번째", "주소 2", 37.2657002, 127.0001002),
        ).toDestinationSelections()

        assertEquals(1, results.size)
        assertEquals("첫 번째", results.single().name)
    }

    private fun place(
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
    ): Place = Place.builder()
        .setDisplayName(name)
        .setFormattedAddress(address)
        .setLocation(LatLng(latitude, longitude))
        .build()
}
