package com.joon.ringout.presentation.destination

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import com.joon.ringout.platform.IosDestinationLocation
import com.joon.ringout.platform.IosDestinationLocationCallback
import com.joon.ringout.platform.IosDestinationLocationError
import com.joon.ringout.platform.IosDestinationMapListener
import com.joon.ringout.platform.IosDestinationSearchCallback
import com.joon.ringout.platform.IosDestinationSearchError
import com.joon.ringout.platform.IosDestinationPlace
import com.joon.ringout.platform.LocalIosNativeServices
import kotlin.time.Clock

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformDestinationMap(
    initialLatitude: Double,
    initialLongitude: Double,
    cameraTarget: DestinationSelection?,
    currentLocationRequestId: Int,
    currentLocationCancellationId: Int,
    onCameraMoveStarted: () -> Unit,
    onCameraSettled: (
        latitude: Double,
        longitude: Double,
    ) -> Unit,
    onAddressResolved: (
        latitude: Double,
        longitude: Double,
        placeName: String?,
        address: String?,
    ) -> Unit,
    onCurrentLocationLoadingChange: (Boolean) -> Unit,
    onCurrentLocationError: (String) -> Unit,
    onMapError: (String) -> Unit,
    modifier: Modifier,
) {
    val nativeServices = LocalIosNativeServices.current
    val isDarkModeEnabled = LocalRingoutThemeMode.current == ThemeMode.Dark
    val locationService = remember(nativeServices) {
        nativeServices.destinationLocationService()
    }
    val currentOnCameraMoveStarted by rememberUpdatedState(onCameraMoveStarted)
    val currentOnCameraSettled by rememberUpdatedState(onCameraSettled)
    val currentOnAddressResolved by rememberUpdatedState(onAddressResolved)
    val currentOnCurrentLocationLoadingChange by rememberUpdatedState(
        onCurrentLocationLoadingChange,
    )
    val currentOnCurrentLocationError by rememberUpdatedState(onCurrentLocationError)
    val currentOnMapError by rememberUpdatedState(onMapError)
    var activeLocationRequestId by remember { mutableStateOf<Int?>(null) }
    var fallbackLocationQuality by remember {
        mutableStateOf<DestinationLocationQuality?>(null)
    }
    var cameraCommandId by remember { mutableIntStateOf(0) }

    fun cancelCurrentLocation() {
        activeLocationRequestId?.let(locationService::cancel)
        activeLocationRequestId = null
        fallbackLocationQuality = null
        currentOnCurrentLocationLoadingChange(false)
    }

    val mapListener = remember {
        object : IosDestinationMapListener {
            override fun onCameraMoveStarted(isGesture: Boolean) {
                if (isGesture) cancelCurrentLocation()
                currentOnCameraMoveStarted()
            }

            override fun onCameraSettled(
                latitude: Double,
                longitude: Double,
            ) {
                currentOnCameraSettled(latitude, longitude)
            }

            override fun onAddressResolved(
                generation: Long,
                latitude: Double,
                longitude: Double,
                placeName: String?,
                address: String?,
            ) {
                currentOnAddressResolved(latitude, longitude, placeName, address)
            }

            override fun onMapError(code: String) {
                currentOnMapError(code.toDestinationMapErrorMessage())
            }
        }
    }
    val mapController = remember(
        nativeServices,
        initialLatitude,
        initialLongitude,
        mapListener,
    ) {
        nativeServices.createDestinationMapController(
            initialLatitude = initialLatitude,
            initialLongitude = initialLongitude,
            listener = mapListener,
        )?.also { controller ->
            controller.setDarkModeEnabled(isDarkModeEnabled)
        }
    }

    if (mapController == null) {
        LaunchedEffect(Unit) {
            currentOnMapError(MapUnavailableError)
        }
        UnavailableDestinationMap(modifier)
        return
    }

    LaunchedEffect(mapController, isDarkModeEnabled) {
        mapController.setDarkModeEnabled(isDarkModeEnabled)
    }

    fun moveToLocation(location: IosDestinationLocation) {
        cameraCommandId += 1
        mapController.moveCamera(
            latitude = location.latitude,
            longitude = location.longitude,
            commandId = cameraCommandId,
        )
    }

    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        cancelCurrentLocation()
        cameraCommandId += 1
        mapController.moveCamera(
            latitude = target.latitude,
            longitude = target.longitude,
            commandId = cameraCommandId,
        )
    }

    LaunchedEffect(currentLocationRequestId) {
        if (currentLocationRequestId <= 0) return@LaunchedEffect

        cancelCurrentLocation()
        activeLocationRequestId = currentLocationRequestId
        currentOnCurrentLocationLoadingChange(true)
        locationService.request(
            requestId = currentLocationRequestId,
            callback = object : IosDestinationLocationCallback {
                override fun onLocation(
                    requestId: Int,
                    location: IosDestinationLocation,
                    isFinal: Boolean,
                ) {
                    if (activeLocationRequestId != requestId) return
                    if (!location.hasValidDestinationCoordinates()) {
                        if (isFinal) {
                            activeLocationRequestId = null
                            fallbackLocationQuality = null
                            currentOnCurrentLocationLoadingChange(false)
                            currentOnCurrentLocationError(CurrentLocationUnavailableError)
                        }
                        return
                    }
                    if (!location.hasFullAccuracy) {
                        locationService.cancel(requestId)
                        activeLocationRequestId = null
                        fallbackLocationQuality = null
                        currentOnCurrentLocationLoadingChange(false)
                        currentOnCurrentLocationError(CurrentLocationReducedAccuracyError)
                        return
                    }

                    val quality = location.destinationLocationQuality()
                    when (
                        decideDestinationLocation(
                            quality = quality,
                            granularity = DestinationLocationGranularity.Fine,
                        )
                    ) {
                        DestinationLocationDecision.UseFinal -> {
                            val fallback = fallbackLocationQuality
                            if (
                                fallback == null ||
                                shouldUseRefinedDestinationLocation(fallback, quality)
                            ) {
                                moveToLocation(location)
                                locationService.cancel(requestId)
                                activeLocationRequestId = null
                                fallbackLocationQuality = null
                                currentOnCurrentLocationLoadingChange(false)
                            } else if (isFinal) {
                                activeLocationRequestId = null
                                fallbackLocationQuality = null
                                currentOnCurrentLocationLoadingChange(false)
                                currentOnCurrentLocationError(CurrentLocationUnavailableError)
                            }
                        }

                        DestinationLocationDecision.UseAndRefine -> {
                            if (fallbackLocationQuality == null) {
                                fallbackLocationQuality = quality
                                moveToLocation(location)
                            }
                            if (isFinal) {
                                activeLocationRequestId = null
                                fallbackLocationQuality = null
                                currentOnCurrentLocationLoadingChange(false)
                                currentOnCurrentLocationError(CurrentLocationUnavailableError)
                            }
                        }

                        DestinationLocationDecision.Reject -> if (isFinal) {
                            activeLocationRequestId = null
                            fallbackLocationQuality = null
                            currentOnCurrentLocationLoadingChange(false)
                            currentOnCurrentLocationError(CurrentLocationUnavailableError)
                        }
                    }
                }

                override fun onError(
                    requestId: Int,
                    error: IosDestinationLocationError,
                ) {
                    if (activeLocationRequestId != requestId) return
                    activeLocationRequestId = null
                    fallbackLocationQuality = null
                    currentOnCurrentLocationLoadingChange(false)
                    currentOnCurrentLocationError(error.userMessage)
                }

                override fun onCancelled(requestId: Int) {
                    if (activeLocationRequestId != requestId) return
                    activeLocationRequestId = null
                    fallbackLocationQuality = null
                    currentOnCurrentLocationLoadingChange(false)
                }
            },
        )
    }

    LaunchedEffect(currentLocationCancellationId) {
        if (currentLocationCancellationId > 0) cancelCurrentLocation()
    }

    DisposableEffect(mapController, locationService) {
        onDispose {
            cancelCurrentLocation()
        }
    }

    UIKitView(
        factory = mapController::view,
        modifier = modifier.fillMaxSize(),
        onRelease = {
            cancelCurrentLocation()
            mapController.dispose()
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = false,
        ),
    )
}

@Composable
actual fun PlatformDestinationSearchEffect(
    query: String?,
    requestId: Int,
    onLoadingChange: (Boolean) -> Unit,
    onResults: (List<DestinationSelection>) -> Unit,
    onError: (String) -> Unit,
) {
    val nativeServices = LocalIosNativeServices.current
    val searchService = remember(nativeServices) {
        nativeServices.destinationSearchService()
    }
    val currentOnLoadingChange by rememberUpdatedState(onLoadingChange)
    val currentOnResults by rememberUpdatedState(onResults)
    val currentOnError by rememberUpdatedState(onError)

    DisposableEffect(query, requestId, searchService) {
        if (query.isNullOrBlank()) {
            currentOnLoadingChange(false)
            return@DisposableEffect onDispose { }
        }
        if (!nativeServices.isPlacesAvailable()) {
            currentOnLoadingChange(false)
            currentOnError(SearchUnavailableError)
            return@DisposableEffect onDispose { }
        }

        var isActive = true
        val expectedRequestId = requestId
        currentOnLoadingChange(true)
        searchService.search(
            query = query,
            requestId = requestId,
            callback = object : IosDestinationSearchCallback {
                override fun onSuccess(
                    requestId: Int,
                    places: List<IosDestinationPlace>,
                ) {
                    if (!isActive || requestId != expectedRequestId) return
                    currentOnLoadingChange(false)
                    currentOnResults(places.map(IosDestinationPlace::toDestinationSelection))
                }

                override fun onError(
                    requestId: Int,
                    error: IosDestinationSearchError,
                ) {
                    if (!isActive || requestId != expectedRequestId) return
                    currentOnLoadingChange(false)
                    currentOnError(error.userMessage)
                }

                override fun onCancelled(requestId: Int) {
                    if (!isActive || requestId != expectedRequestId) return
                    currentOnLoadingChange(false)
                }
            },
        )

        onDispose {
            isActive = false
            searchService.cancel(requestId)
            currentOnLoadingChange(false)
        }
    }
}

@Composable
private fun UnavailableDestinationMap(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFDCE8DF)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = MapUnavailableError,
            color = Color(0xFF6E756F),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun IosDestinationLocation.hasValidDestinationCoordinates(): Boolean =
    latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0

private fun IosDestinationLocation.destinationLocationQuality(): DestinationLocationQuality =
    DestinationLocationQuality(
        ageMillis = Clock.System.now().toEpochMilliseconds() - timestampEpochMillis,
        accuracyMeters = horizontalAccuracyMeters,
    )

private fun IosDestinationPlace.toDestinationSelection(): DestinationSelection =
    DestinationSelection(
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
    )

private val IosDestinationSearchError.userMessage: String
    get() = when (this) {
        IosDestinationSearchError.NETWORK ->
            "네트워크 연결을 확인한 뒤 다시 검색해 주세요."

        IosDestinationSearchError.QUOTA_EXCEEDED ->
            "Google Places 검색 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."

        IosDestinationSearchError.REQUEST_DENIED ->
            "Google Places API(New) 설정을 확인해 주세요."

        IosDestinationSearchError.INVALID_REQUEST,
        IosDestinationSearchError.UNKNOWN,
        -> SearchFailedError
    }

private val IosDestinationLocationError.userMessage: String
    get() = when (this) {
        IosDestinationLocationError.PERMISSION_DENIED,
        IosDestinationLocationError.RESTRICTED,
        -> "현재 위치를 사용하려면 위치 권한이 필요합니다."

        IosDestinationLocationError.SERVICES_DISABLED ->
            "위치 서비스가 꺼져 있습니다. 설정에서 위치 서비스를 켜 주세요."

        IosDestinationLocationError.REDUCED_ACCURACY ->
            CurrentLocationReducedAccuracyError

        IosDestinationLocationError.UNAVAILABLE ->
            CurrentLocationUnavailableError
    }

private fun String.toDestinationMapErrorMessage(): String = when (this) {
    "unavailable" -> MapUnavailableError
    "camera_move_failed" -> "지도를 이동하지 못했습니다. 잠시 후 다시 시도해 주세요."
    else -> "지도를 표시하지 못했습니다. 잠시 후 다시 시도해 주세요."
}

private const val MapUnavailableError =
    "Google 지도 API 설정을 확인해 주세요. 검색과 저장된 목적지는 계속 사용할 수 있습니다."
private const val SearchUnavailableError =
    "Google Places 검색을 시작하지 못했습니다. API 설정을 확인해 주세요."
private const val SearchFailedError = "검색 중 오류가 발생했습니다."
private const val CurrentLocationUnavailableError =
    "현재 위치를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요."
private const val CurrentLocationReducedAccuracyError =
    "현재 위치를 정확히 설정하려면 정확한 위치를 허용해 주세요."
