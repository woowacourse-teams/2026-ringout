package com.joon.ringout.presentation.destination

import com.joon.ringout.domain.destination.SavedDestination

internal fun DestinationSelection.isConfiguredDestination(): Boolean =
    name.isNotBlank() &&
        latitude.isFinite() &&
        latitude in ValidLatitudeRange &&
        longitude.isFinite() &&
        longitude in ValidLongitudeRange

internal fun canConfirmDestination(
    isCameraMoving: Boolean,
    isLocatingCurrentLocation: Boolean,
    mapError: String?,
    isSaveInProgress: Boolean = false,
): Boolean =
    !isCameraMoving && !isLocatingCurrentLocation && !isSaveInProgress && mapError == null

internal fun destinationAtCameraPosition(
    cameraTarget: DestinationSelection?,
    latitude: Double,
    longitude: Double,
): DestinationSelection =
    cameraTarget?.takeIf { it.hasSameCoordinates(latitude, longitude) }
        ?: DestinationSelection(
            name = SelectedDestinationFallbackName,
            address = ResolvingDestinationAddress,
            latitude = latitude,
            longitude = longitude,
        )

internal fun DestinationSelection.withResolvedAddress(
    latitude: Double,
    longitude: Double,
    placeName: String?,
    address: String?,
): DestinationSelection {
    if (!hasSameCoordinates(latitude, longitude)) return this

    return copy(
        name = placeName?.takeIf(String::isNotBlank) ?: name,
        address = address?.takeIf(String::isNotBlank) ?: UnavailableDestinationAddress,
    )
}

internal fun shouldApplyResolvedAddress(
    isResolvingAddress: Boolean,
    selection: DestinationSelection,
    cameraTarget: DestinationSelection?,
    latitude: Double,
    longitude: Double,
): Boolean =
    isResolvingAddress &&
        selection.hasSameCoordinates(latitude, longitude) &&
        cameraTarget?.hasSameCoordinates(latitude, longitude) != true

internal fun DestinationSelection.withNicknameForSave(nickname: String): DestinationSelection =
    copy(
        name = nickname,
        address = if (address == ResolvingDestinationAddress) {
            UnavailableDestinationAddress
        } else {
            address
        },
    )

internal fun DestinationSelection.hasSameCoordinates(
    latitude: Double,
    longitude: Double,
): Boolean =
    kotlin.math.abs(this.latitude - latitude) < DestinationCoordinateTolerance &&
        kotlin.math.abs(this.longitude - longitude) < DestinationCoordinateTolerance

internal fun List<SavedDestination>.findAtLocation(
    selection: DestinationSelection,
): SavedDestination? =
    firstOrNull { savedDestination ->
        selection.hasSameCoordinates(
            latitude = savedDestination.latitude,
            longitude = savedDestination.longitude,
        )
    }

internal fun SavedDestination.toDestinationSelection(): DestinationSelection =
    DestinationSelection(
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
    )

internal fun DestinationSelection.toSavedDestination(id: Long = 0L): SavedDestination =
    SavedDestination(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
    )

internal const val SelectedDestinationFallbackName = "선택한 위치"
internal const val ResolvingDestinationAddress = "주소를 확인하는 중입니다."
internal const val UnavailableDestinationAddress = "주소를 확인할 수 없는 위치"

private val ValidLatitudeRange = -90.0..90.0
private val ValidLongitudeRange = -180.0..180.0
private const val DestinationCoordinateTolerance = 0.00001
