package com.joon.ringout.presentation.destination

import com.joon.ringout.domain.destination.SavedDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DestinationSelectionStateTest {
    @Test
    fun `저장하지 않는 목적지는 확인된 주소를 라벨로 사용하고 좌표를 유지한다`() {
        val selection = DestinationSelection("검색한 장소", "대한민국 서울 중구 세종대로 110", 37.5665, 126.978)
        assertEquals(
            selection.copy(name = "대한민국 서울 중구 세종대로 110"),
            selection.withAddressAsName(),
        )
    }

    @Test
    fun `주소를 확인할 수 없으면 선택 라벨을 변경하지 않는다`() {
        for (address in listOf("", " ", ResolvingDestinationAddress, UnavailableDestinationAddress)) {
            val selection = DestinationSelection("검색한 장소", address, 37.5665, 126.978)
            assertEquals(selection, selection.withAddressAsName())
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
    fun `주소를 확인할 수 없는 위치는 목적지로 확정할 수 없다`() {
        assertFalse(
            canConfirmDestination(
                isCameraMoving = false,
                isLocatingCurrentLocation = false,
                mapError = null,
                isAddressAvailable = false,
            ),
        )
    }

    @Test
    fun `주소를 확인한 위치는 목적지로 확정할 수 있다`() {
        assertTrue(
            canConfirmDestination(
                isCameraMoving = false,
                isLocatingCurrentLocation = false,
                mapError = null,
                isAddressAvailable = true,
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
    fun `저장 목적지는 선택 라벨과 좌표만 복사한다`() {
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
                label = "회사",
                latitude = 37.5665,
                longitude = 126.9780,
            ),
            savedDestination,
        )
        assertEquals(selection.copy(address = ""), savedDestination.toDestinationSelection())
    }

    @Test
    fun `저장된 목적지를 선택하면 라벨과 좌표를 복사한다`() {
        val savedDestination = SavedDestination(
            id = 42L,
            label = "회사",
            latitude = 37.5665,
            longitude = 126.9780,
        )
        assertEquals("회사", savedDestination.toDestinationSelection().name)
        assertEquals(savedDestination.latitude, savedDestination.toDestinationSelection().latitude)
        assertEquals(savedDestination.longitude, savedDestination.toDestinationSelection().longitude)
    }

    @Test
    fun `저장된 목적지와 좌표가 같아도 새 지도 선택에 별칭을 복사하지 않는다`() {
        val savedDestination = SavedDestination(
            id = 42L,
            label = "회사",
            latitude = 37.5665,
            longitude = 126.9780,
        )
        val mapSelection = destinationAtCameraPosition(
            cameraTarget = null,
            latitude = savedDestination.latitude,
            longitude = savedDestination.longitude,
        )

        assertEquals(SelectedDestinationFallbackName, mapSelection.name)
        assertTrue(mapSelection.requiresAddressResolution())
    }
}
