package com.joon.ringout.presentation.destination

import com.joon.ringout.domain.destination.SavedDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DestinationSelectionStateTest {
    @Test
    fun `저장하지 않는 목적지는 주소를 이름으로 사용하고 좌표를 유지한다`() {
        val selection = DestinationSelection("검색한 장소", "대한민국 서울 중구 세종대로 110", 37.5665, 126.978)
        assertEquals(
            selection.copy(name = "서울 중구 세종대로 110"),
            selection.withAddressAsName(),
        )
    }

    @Test
    fun `저장하지 않을 때 주소 조회 중 문구나 빈 주소를 알람 이름으로 남기지 않는다`() {
        for (address in listOf("", " ", ResolvingDestinationAddress)) {
            val selection = DestinationSelection("검색한 장소", address, 37.5665, 126.978)
            assertEquals(
                selection.copy(name = UnavailableDestinationAddress, address = UnavailableDestinationAddress),
                selection.withAddressAsName(),
            )
        }
    }

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
    fun destinationCannotBeConfirmedWhileRoomSaveIsInProgress() {
        assertFalse(
            canConfirmDestination(
                isCameraMoving = false,
                isLocatingCurrentLocation = false,
                mapError = null,
                isSaveInProgress = true,
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
    fun savedDestinationWithoutAddressStartsAddressResolutionAtCameraPosition() {
        val savedDestination = DestinationSelection(
            name = "회사",
            address = "",
            latitude = 37.5665,
            longitude = 126.9780,
        )

        val settled = destinationAtCameraPosition(
            cameraTarget = savedDestination,
            latitude = savedDestination.latitude,
            longitude = savedDestination.longitude,
        )

        assertEquals("회사", settled.name)
        assertEquals(ResolvingDestinationAddress, settled.address)
        assertTrue(settled.requiresAddressResolution())
    }

    @Test
    fun `주소 조회가 완료되어도 저장된 목적지의 별명은 유지한다`() {
        val savedDestination = DestinationSelection(
            name = "회사",
            address = ResolvingDestinationAddress,
            latitude = 37.5665,
            longitude = 126.9780,
        )

        assertEquals(
            savedDestination.copy(address = "서울특별시 중구 세종대로 110"),
            savedDestination.withResolvedAddress(
                latitude = savedDestination.latitude,
                longitude = savedDestination.longitude,
                address = "서울특별시 중구 세종대로 110",
            ),
        )
    }

    @Test
    fun `핀 위치의 주소가 조회되어도 이름은 선택한 위치로 유지하고 다른 좌표의 결과는 무시한다`() {
        val pending = DestinationSelection(
            name = SelectedDestinationFallbackName,
            address = ResolvingDestinationAddress,
            latitude = 37.5665,
            longitude = 126.9780,
        )

        assertEquals(
            pending.copy(
                address = "서울특별시 중구 세종대로 110",
            ),
            pending.withResolvedAddress(
                latitude = 37.5665,
                longitude = 126.9780,
                address = "서울특별시 중구 세종대로 110",
            ),
        )
        assertEquals(
            pending,
            pending.withResolvedAddress(
                latitude = 35.1796,
                longitude = 129.0756,
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

    @Test
    fun destinationSelectionMapsEverySavedRoomField() {
        val selection = DestinationSelection(
            name = "회사",
            address = "서울특별시 중구 세종대로 110",
            latitude = 37.5665,
            longitude = 126.9780,
        )
        val savedDestination = selection.toSavedDestination(id = 42L)

        assertEquals(
            SavedDestination(
                id = 42L,
                name = "회사",
                address = "서울특별시 중구 세종대로 110",
                latitude = 37.5665,
                longitude = 126.9780,
            ),
            savedDestination,
        )
        assertEquals(selection, savedDestination.toDestinationSelection())
    }

    @Test
    fun savedDestinationIsMatchedByLocationEvenWhenNicknameAndAddressDiffer() {
        val savedDestination = SavedDestination(
            id = 42L,
            name = "회사",
            address = "저장된 주소",
            latitude = 37.5665,
            longitude = 126.9780,
        )
        val restoredSelection = DestinationSelection(
            name = "별명이 달라진 회사",
            address = "새로 확인된 주소",
            latitude = savedDestination.latitude,
            longitude = savedDestination.longitude,
        )

        assertEquals(
            savedDestination,
            listOf(savedDestination).findAtLocation(restoredSelection),
        )
    }

    @Test
    fun savedDestinationIsNotMatchedByNicknameWhenLocationDiffers() {
        val savedDestination = SavedDestination(
            id = 42L,
            name = "회사",
            address = "서울특별시 중구 세종대로 110",
            latitude = 37.5665,
            longitude = 126.9780,
        )
        val differentLocation = DestinationSelection(
            name = savedDestination.name,
            address = savedDestination.address,
            latitude = 35.1796,
            longitude = 129.0756,
        )

        assertNull(listOf(savedDestination).findAtLocation(differentLocation))
    }
}
