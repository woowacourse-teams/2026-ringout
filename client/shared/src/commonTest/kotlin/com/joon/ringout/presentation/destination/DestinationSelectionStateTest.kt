package com.joon.ringout.presentation.destination

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun destinationCanBeConfirmedWhileOnlyAddressIsResolving() {
        assertTrue(
            canConfirmDestination(
                isCameraMoving = false,
                isLocatingCurrentLocation = false,
                mapError = null,
            ),
        )
    }

    @Test
    fun destinationCannotBeConfirmedWhileCameraOrLocationIsMoving() {
        assertFalse(
            canConfirmDestination(
                isCameraMoving = true,
                isLocatingCurrentLocation = false,
                mapError = null,
            ),
        )
        assertFalse(
            canConfirmDestination(
                isCameraMoving = false,
                isLocatingCurrentLocation = true,
                mapError = null,
            ),
        )
    }

    @Test
    fun cameraPositionUsesResolvingFallbackUntilAddressArrives() {
        assertEquals(
            DestinationSelection(
                name = SelectedDestinationFallbackName,
                address = ResolvingDestinationAddress,
                latitude = 37.5665,
                longitude = 126.9780,
            ),
            destinationAtCameraPosition(
                cameraTarget = null,
                latitude = 37.5665,
                longitude = 126.9780,
            ),
        )
    }

    @Test
    fun matchingSearchResultIsPreservedAtCameraPosition() {
        val searchResult = DestinationSelection(
            name = "서울시청",
            address = "서울특별시 중구 세종대로 110",
            latitude = 37.5665,
            longitude = 126.9780,
        )

        assertEquals(
            searchResult,
            destinationAtCameraPosition(
                cameraTarget = searchResult,
                latitude = 37.5665,
                longitude = 126.9780,
            ),
        )
    }

    @Test
    fun resolvedAddressUpdatesOnlyMatchingCameraPosition() {
        val pending = DestinationSelection(
            name = SelectedDestinationFallbackName,
            address = ResolvingDestinationAddress,
            latitude = 37.5665,
            longitude = 126.9780,
        )

        assertEquals(
            pending.copy(
                name = "서울시청",
                address = "서울특별시 중구 세종대로 110",
            ),
            pending.withResolvedAddress(
                latitude = 37.5665,
                longitude = 126.9780,
                placeName = "서울시청",
                address = "서울특별시 중구 세종대로 110",
            ),
        )
        assertEquals(
            pending,
            pending.withResolvedAddress(
                latitude = 35.1796,
                longitude = 129.0756,
                placeName = "부산시청",
                address = "부산광역시 연제구 중앙대로 1001",
            ),
        )
    }

    @Test
    fun staleAddressDoesNotOverwriteAMatchingSearchTarget() {
        val searchTarget = DestinationSelection(
            name = "서울시청",
            address = "서울특별시 중구 세종대로 110",
            latitude = 37.5665,
            longitude = 126.9780,
        )

        assertFalse(
            shouldApplyResolvedAddress(
                isResolvingAddress = true,
                selection = searchTarget,
                cameraTarget = searchTarget,
                latitude = searchTarget.latitude,
                longitude = searchTarget.longitude,
            ),
        )
        assertTrue(
            shouldApplyResolvedAddress(
                isResolvingAddress = true,
                selection = searchTarget,
                cameraTarget = null,
                latitude = searchTarget.latitude,
                longitude = searchTarget.longitude,
            ),
        )
    }

    @Test
    fun unresolvedAddressIsConvertedToStableFallbackWhenSaved() {
        val selection = DestinationSelection(
            name = SelectedDestinationFallbackName,
            address = ResolvingDestinationAddress,
            latitude = 37.5665,
            longitude = 126.9780,
        )

        assertEquals(
            selection.copy(
                name = "회사",
                address = UnavailableDestinationAddress,
            ),
            selection.withNicknameForSave("회사"),
        )
    }
}
