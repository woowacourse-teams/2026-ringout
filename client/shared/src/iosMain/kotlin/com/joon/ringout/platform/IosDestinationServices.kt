package com.joon.ringout.platform

import platform.UIKit.UIView

data class IosDestinationPlace(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

enum class IosDestinationSearchError {
    NETWORK,
    QUOTA_EXCEEDED,
    REQUEST_DENIED,
    INVALID_REQUEST,
    UNKNOWN,
}

interface IosDestinationSearchCallback {
    fun onSuccess(
        requestId: Int,
        places: List<IosDestinationPlace>,
    )

    fun onError(
        requestId: Int,
        error: IosDestinationSearchError,
    )

    fun onCancelled(requestId: Int)
}

interface IosDestinationSearchService {
    fun search(
        query: String,
        requestId: Int,
        callback: IosDestinationSearchCallback,
    )

    fun cancel(requestId: Int)
}

data class IosDestinationLocation(
    val latitude: Double,
    val longitude: Double,
    val timestampEpochMillis: Long,
    val horizontalAccuracyMeters: Float,
    val hasFullAccuracy: Boolean,
)

enum class IosDestinationLocationError {
    PERMISSION_DENIED,
    RESTRICTED,
    SERVICES_DISABLED,
    REDUCED_ACCURACY,
    UNAVAILABLE,
}

interface IosDestinationLocationCallback {
    fun onLocation(
        requestId: Int,
        location: IosDestinationLocation,
        isFinal: Boolean,
    )

    fun onError(
        requestId: Int,
        error: IosDestinationLocationError,
    )

    fun onCancelled(requestId: Int)
}

interface IosDestinationLocationService {
    fun request(
        requestId: Int,
        callback: IosDestinationLocationCallback,
    )

    fun cancel(requestId: Int)
}

interface IosDestinationMapListener {
    fun onCameraMoveStarted(isGesture: Boolean)

    fun onCameraSettled(
        latitude: Double,
        longitude: Double,
    )

    fun onAddressResolved(
        generation: Long,
        latitude: Double,
        longitude: Double,
        placeName: String?,
        address: String?,
    )

    fun onMapError(code: String)
}

interface IosDestinationMapController {
    fun view(): UIView

    fun setDarkModeEnabled(isEnabled: Boolean)

    fun moveCamera(
        latitude: Double,
        longitude: Double,
        commandId: Int,
    )

    fun dispose()
}

interface IosActiveMissionMapController {
    fun view(): UIView

    fun setDarkModeEnabled(isEnabled: Boolean)

    fun updateCurrentLocation(
        latitude: Double,
        longitude: Double,
    )

    fun clearCurrentLocation()

    fun dispose()
}
