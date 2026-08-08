package com.joon.ringout.presentation.destination

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LastLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnCameraMoveStarted = rememberUpdatedState(onCameraMoveStarted)
    val currentOnCameraSettled = rememberUpdatedState(onCameraSettled)
    val currentOnAddressResolved = rememberUpdatedState(onAddressResolved)
    val currentOnCurrentLocationLoadingChange = rememberUpdatedState(onCurrentLocationLoadingChange)
    val currentOnCurrentLocationError = rememberUpdatedState(onCurrentLocationError)
    val currentOnMapError = rememberUpdatedState(onMapError)
    val initialPosition = remember(initialLatitude, initialLongitude) {
        LatLng(initialLatitude, initialLongitude)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPosition, InitialZoomLevel)
    }
    val reverseGeocoder = remember(context) {
        AndroidDestinationReverseGeocoder(context.applicationContext)
    }
    val currentLocationRequester = remember(context) {
        DestinationCurrentLocationRequester(context.applicationContext)
    }
    val mapUiSettings = remember {
        MapUiSettings(
            compassEnabled = true,
            indoorLevelPickerEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
        )
    }
    var activeCurrentLocationRequestId by remember { mutableStateOf<Int?>(null) }
    var lastHandledCurrentLocationRequestId by remember { mutableStateOf(0) }
    var lastHandledCurrentLocationCancellationId by remember { mutableStateOf(0) }
    var currentLocationCameraJob by remember { mutableStateOf<Job?>(null) }
    var currentLocationCameraGeneration by remember { mutableStateOf(0) }
    val cameraSettlementTracker = remember { DestinationCameraSettlementTracker() }
    val reverseGeocodeJobHolder = remember { DestinationReverseGeocodeJobHolder() }

    fun cancelReverseGeocoding() {
        reverseGeocodeJobHolder.job?.cancel()
        reverseGeocodeJobHolder.job = null
    }

    fun invalidateCameraSettlement() {
        cameraSettlementTracker.invalidateSettlement()
        cancelReverseGeocoding()
    }

    fun notifyCameraMoveStarted() {
        invalidateCameraSettlement()
        currentOnCameraMoveStarted.value()
    }

    fun settleCameraAndResolveAddress(center: LatLng) {
        val settlementGeneration = cameraSettlementTracker.registerSettlement(
            latitude = center.latitude,
            longitude = center.longitude,
        ) ?: return

        currentOnCameraSettled.value(center.latitude, center.longitude)
        cancelReverseGeocoding()
        reverseGeocodeJobHolder.job = coroutineScope.launch {
            val resolved = reverseGeocoder.resolve(
                latitude = center.latitude,
                longitude = center.longitude,
            )
            if (
                cameraSettlementTracker.isCurrentSettlement(
                    generation = settlementGeneration,
                    latitude = center.latitude,
                    longitude = center.longitude,
                )
            ) {
                currentOnAddressResolved.value(
                    center.latitude,
                    center.longitude,
                    resolved?.placeName,
                    resolved?.address,
                )
            }
        }
    }

    fun cancelCurrentLocationRequest() {
        currentLocationCameraGeneration += 1
        currentLocationCameraJob?.cancel()
        currentLocationCameraJob = null
        activeCurrentLocationRequestId = null
        currentLocationRequester.cancel()
        currentOnCurrentLocationLoadingChange.value(false)
    }

    fun requestAndMoveToCurrentLocation(requestId: Int) {
        if (activeCurrentLocationRequestId != requestId) return

        currentOnCurrentLocationLoadingChange.value(true)
        var lastPresentedLocation: Location? = null
        currentLocationRequester.request(
            onLocation = locationCallback@{ location, isFinal ->
                if (activeCurrentLocationRequestId != requestId) return@locationCallback
                if (isFinal) {
                    activeCurrentLocationRequestId = null
                }
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    cancelCurrentLocationRequest()
                    return@locationCallback
                }

                val shouldMoveCamera = lastPresentedLocation?.isSameMapFixAs(location) != true
                lastPresentedLocation = location
                if (!shouldMoveCamera) {
                    currentOnCurrentLocationLoadingChange.value(false)
                    return@locationCallback
                }

                invalidateCameraSettlement()
                currentLocationCameraGeneration += 1
                val animationGeneration = currentLocationCameraGeneration
                currentLocationCameraJob?.cancel()
                currentLocationCameraJob = coroutineScope.launch {
                    try {
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(
                                LatLng(location.latitude, location.longitude),
                                CurrentLocationZoomLevel,
                            ),
                            durationMs = CameraAnimationMillis,
                        )
                        settleCameraAndResolveAddress(
                            LatLng(location.latitude, location.longitude),
                        )
                        currentOnCurrentLocationLoadingChange.value(false)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        if (
                            currentLocationCameraGeneration == animationGeneration &&
                            (activeCurrentLocationRequestId == requestId || isFinal)
                        ) {
                            activeCurrentLocationRequestId = null
                            currentLocationRequester.cancel()
                            currentOnCurrentLocationLoadingChange.value(false)
                            currentOnCurrentLocationError.value(CurrentLocationMoveError)
                        }
                    } finally {
                        if (currentLocationCameraGeneration == animationGeneration) {
                            currentLocationCameraJob = null
                        }
                    }
                }
            },
            onError = { error ->
                if (activeCurrentLocationRequestId != requestId) return@request
                activeCurrentLocationRequestId = null
                currentLocationCameraGeneration += 1
                currentLocationCameraJob?.cancel()
                currentLocationCameraJob = null
                currentOnCurrentLocationLoadingChange.value(false)
                currentOnCurrentLocationError.value(error)
            },
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val requestId = activeCurrentLocationRequestId
            ?: return@rememberLauncherForActivityResult
        val isGranted = permissions.values.any { it } || context.hasDestinationLocationPermission()
        if (isGranted) {
            requestAndMoveToCurrentLocation(requestId)
        } else {
            activeCurrentLocationRequestId = null
            currentOnCurrentLocationLoadingChange.value(false)
            currentOnCurrentLocationError.value(CurrentLocationPermissionError)
        }
    }

    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        invalidateCameraSettlement()
        cancelCurrentLocationRequest()
        try {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(
                    LatLng(target.latitude, target.longitude),
                    CurrentLocationZoomLevel,
                ),
                durationMs = CameraAnimationMillis,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            currentOnMapError.value(MapCameraMoveError)
        }
    }

    LaunchedEffect(currentLocationRequestId) {
        if (currentLocationRequestId <= lastHandledCurrentLocationRequestId) {
            return@LaunchedEffect
        }

        lastHandledCurrentLocationRequestId = currentLocationRequestId
        cancelCurrentLocationRequest()
        activeCurrentLocationRequestId = currentLocationRequestId
        currentOnCurrentLocationLoadingChange.value(true)
        if (context.hasDestinationLocationPermission()) {
            requestAndMoveToCurrentLocation(currentLocationRequestId)
        } else {
            locationPermissionLauncher.launch(DestinationLocationPermissions)
        }
    }

    LaunchedEffect(currentLocationCancellationId) {
        if (currentLocationCancellationId <= lastHandledCurrentLocationCancellationId) {
            return@LaunchedEffect
        }

        lastHandledCurrentLocationCancellationId = currentLocationCancellationId
        cancelCurrentLocationRequest()
    }

    LaunchedEffect(cameraPositionState) {
        var hasObservedCameraMovement = false
        var wasMoving = false
        try {
            snapshotFlow {
                cameraPositionState.isMoving to cameraPositionState.cameraMoveStartedReason
            }
                .distinctUntilChanged()
                .collect { (isMoving, moveStartedReason) ->
                    if (isMoving) {
                        hasObservedCameraMovement = true
                        if (moveStartedReason == CameraMoveStartedReason.GESTURE) {
                            cancelCurrentLocationRequest()
                        }
                        if (!wasMoving) {
                            currentOnCurrentLocationLoadingChange.value(false)
                            notifyCameraMoveStarted()
                        }
                    } else if (wasMoving && hasObservedCameraMovement) {
                        settleCameraAndResolveAddress(cameraPositionState.position.target)
                    }
                    wasMoving = isMoving
                }
        } finally {
            cancelReverseGeocoding()
        }
    }

    DisposableEffect(lifecycleOwner, currentLocationRequester) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                cancelCurrentLocationRequest()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cancelCurrentLocationRequest()
            cancelReverseGeocoding()
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentPadding = PaddingValues(
            horizontal = MapHorizontalContentPadding,
            vertical = MapVerticalContentPadding,
        ),
        uiSettings = mapUiSettings,
    )
}

internal class DestinationCameraSettlementTracker {
    private var generation = 0L
    private var settledLatitude: Double? = null
    private var settledLongitude: Double? = null

    fun invalidateSettlement() {
        generation += 1
        settledLatitude = null
        settledLongitude = null
    }

    fun registerSettlement(
        latitude: Double,
        longitude: Double,
    ): Long? {
        if (hasSameCoordinates(latitude, longitude)) return null

        generation += 1
        settledLatitude = latitude
        settledLongitude = longitude
        return generation
    }

    fun isCurrentSettlement(
        generation: Long,
        latitude: Double,
        longitude: Double,
    ): Boolean =
        this.generation == generation && hasSameCoordinates(latitude, longitude)

    private fun hasSameCoordinates(
        latitude: Double,
        longitude: Double,
    ): Boolean {
        val currentLatitude = settledLatitude ?: return false
        val currentLongitude = settledLongitude ?: return false
        return kotlin.math.abs(currentLatitude - latitude) < CameraCoordinateTolerance &&
            kotlin.math.abs(currentLongitude - longitude) < CameraCoordinateTolerance
    }
}

private class DestinationReverseGeocodeJobHolder {
    var job: Job? = null
}

private class DestinationCurrentLocationRequester(context: Context) {
    private val applicationContext = context.applicationContext
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(applicationContext)
    private val mainLooper = Looper.getMainLooper()
    private val mainHandler = Handler(mainLooper)
    private val mainExecutor = Executor { command ->
        if (Looper.myLooper() == mainLooper) {
            command.run()
        } else {
            mainHandler.post(command)
        }
    }
    private var nextRequestId = 0L
    private var activeRequestId: Long? = null
    private var activeCancellationTokenSource: CancellationTokenSource? = null

    fun request(
        onLocation: (location: Location, isFinal: Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        cancel()

        if (!applicationContext.hasDestinationLocationPermission()) {
            onError(CurrentLocationPermissionError)
            return
        }

        val hasFinePermission = applicationContext.hasDestinationFineLocationPermission()
        val granularity = if (hasFinePermission) {
            DestinationLocationGranularity.Fine
        } else {
            DestinationLocationGranularity.Coarse
        }
        val requestId = ++nextRequestId
        activeRequestId = requestId
        requestCachedLocation(
            requestId = requestId,
            hasFinePermission = hasFinePermission,
            onResult = { candidate ->
                val decision = candidate?.destinationLocationDecision(granularity)
                    ?: DestinationLocationDecision.Reject
                when (decision) {
                    DestinationLocationDecision.UseFinal -> {
                        val cachedLocation = candidate ?: return@requestCachedLocation
                        finishWithLocation(requestId, cachedLocation, onLocation)
                    }

                    DestinationLocationDecision.UseAndRefine -> {
                        val cachedLocation = candidate ?: return@requestCachedLocation
                        onLocation(cachedLocation, false)
                        requestFreshLocation(
                            requestId = requestId,
                            hasFinePermission = hasFinePermission,
                            granularity = granularity,
                            fallbackLocation = cachedLocation,
                            onLocation = onLocation,
                            onError = onError,
                        )
                    }

                    DestinationLocationDecision.Reject -> {
                        requestFreshLocation(
                            requestId = requestId,
                            hasFinePermission = hasFinePermission,
                            granularity = granularity,
                            fallbackLocation = null,
                            onLocation = onLocation,
                            onError = onError,
                        )
                    }
                }
            },
        )
    }

    fun cancel() {
        activeRequestId = null
        activeCancellationTokenSource?.cancel()
        activeCancellationTokenSource = null
    }

    private fun requestFreshLocation(
        requestId: Long,
        hasFinePermission: Boolean,
        granularity: DestinationLocationGranularity,
        fallbackLocation: Location?,
        onLocation: (location: Location, isFinal: Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        requestSingleLocation(
            requestId = requestId,
            request = currentLocationRequest(
                hasFinePermission = hasFinePermission,
                maxUpdateAgeMillis = 0L,
                durationMillis = CurrentLocationRefinementDurationMillis,
            ),
            onResult = { candidate ->
                val refinedLocation = candidate?.takeIf { location ->
                    location.hasValidDestinationCoordinates() &&
                        location.destinationLocationAgeMillis in
                        -DestinationLocationClockSkewMillis..DestinationLocationFreshMaxAgeMillis &&
                        location.destinationLocationDecision(granularity) !=
                        DestinationLocationDecision.Reject
                }?.takeIf { location ->
                    fallbackLocation == null ||
                        shouldUseRefinedDestinationLocation(
                            fallback = fallbackLocation.destinationLocationQuality,
                            refined = location.destinationLocationQuality,
                        )
                }
                val finalLocation = refinedLocation ?: fallbackLocation
                if (finalLocation == null) {
                    finishWithError(requestId, onError)
                } else {
                    finishWithLocation(requestId, finalLocation, onLocation)
                }
            },
            onFailure = {
                if (fallbackLocation == null) {
                    finishWithError(requestId, onError)
                } else {
                    finishWithLocation(requestId, fallbackLocation, onLocation)
                }
            },
        )
    }

    private fun requestCachedLocation(
        requestId: Long,
        hasFinePermission: Boolean,
        onResult: (Location?) -> Unit,
    ) {
        if (activeRequestId != requestId) return

        val request = LastLocationRequest.Builder()
            .setGranularity(
                if (hasFinePermission) {
                    Granularity.GRANULARITY_FINE
                } else {
                    Granularity.GRANULARITY_COARSE
                },
            )
            .setMaxUpdateAgeMillis(DestinationLocationCacheMaxAgeMillis)
            .build()
        val task = try {
            fusedLocationClient.getLastLocation(request)
        } catch (_: SecurityException) {
            onResult(null)
            return
        } catch (_: Exception) {
            onResult(null)
            return
        }
        task.addOnCompleteListener(mainExecutor) { completedTask ->
            if (activeRequestId != requestId) return@addOnCompleteListener
            onResult(
                if (completedTask.isSuccessful) {
                    completedTask.result
                } else {
                    null
                },
            )
        }
    }

    private fun requestSingleLocation(
        requestId: Long,
        request: CurrentLocationRequest,
        onResult: (Location?) -> Unit,
        onFailure: () -> Unit,
    ) {
        if (activeRequestId != requestId) return

        val cancellationTokenSource = CancellationTokenSource()
        activeCancellationTokenSource = cancellationTokenSource
        val task = try {
            fusedLocationClient.getCurrentLocation(request, cancellationTokenSource.token)
        } catch (_: SecurityException) {
            onFailure()
            return
        } catch (_: Exception) {
            onFailure()
            return
        }
        task.addOnCompleteListener(mainExecutor) { completedTask ->
            if (
                activeRequestId != requestId ||
                activeCancellationTokenSource !== cancellationTokenSource
            ) {
                return@addOnCompleteListener
            }
            if (completedTask.isSuccessful) {
                onResult(completedTask.result)
            } else {
                onFailure()
            }
        }
    }

    private fun currentLocationRequest(
        hasFinePermission: Boolean,
        maxUpdateAgeMillis: Long,
        durationMillis: Long,
    ): CurrentLocationRequest = CurrentLocationRequest.Builder()
        .setPriority(
            if (hasFinePermission) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            },
        )
        .setGranularity(
            if (hasFinePermission) {
                Granularity.GRANULARITY_FINE
            } else {
                Granularity.GRANULARITY_COARSE
            },
        )
        .setMaxUpdateAgeMillis(maxUpdateAgeMillis)
        .setDurationMillis(durationMillis)
        .build()

    private fun finishWithLocation(
        requestId: Long,
        location: Location,
        onLocation: (location: Location, isFinal: Boolean) -> Unit,
    ) {
        if (!finishRequest(requestId)) return
        onLocation(location, true)
    }

    private fun finishWithError(
        requestId: Long,
        onError: (String) -> Unit,
    ) {
        if (!finishRequest(requestId)) return
        onError(CurrentLocationUnavailableError)
    }

    private fun finishRequest(requestId: Long): Boolean {
        if (activeRequestId != requestId) return false
        activeRequestId = null
        activeCancellationTokenSource = null
        return true
    }
}

private fun Context.hasDestinationFineLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.hasDestinationLocationPermission(): Boolean =
    hasDestinationFineLocationPermission() ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Location.hasValidDestinationCoordinates(): Boolean =
    latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0

private val Location.destinationLocationAgeMillis: Long
    get() {
        val currentElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        val locationElapsedRealtimeNanos = elapsedRealtimeNanos
        return if (locationElapsedRealtimeNanos > 0L) {
            (currentElapsedRealtimeNanos - locationElapsedRealtimeNanos) / NanosPerMillisecond
        } else {
            System.currentTimeMillis() - time
        }
    }

private val Location.mapAccuracyMeters: Float
    get() = accuracy.takeIf { hasAccuracy() && it.isFinite() && it >= 0f }
        ?: Float.POSITIVE_INFINITY

private val Location.destinationLocationQuality: DestinationLocationQuality
    get() = DestinationLocationQuality(
        ageMillis = destinationLocationAgeMillis,
        accuracyMeters = mapAccuracyMeters,
    )

private fun Location.destinationLocationDecision(
    granularity: DestinationLocationGranularity,
): DestinationLocationDecision =
    if (!hasValidDestinationCoordinates()) {
        DestinationLocationDecision.Reject
    } else {
        decideDestinationLocation(
            quality = destinationLocationQuality,
            granularity = granularity,
        )
    }

private fun Location.isSameMapFixAs(other: Location): Boolean =
    this === other ||
        (
            elapsedRealtimeNanos == other.elapsedRealtimeNanos &&
                latitude == other.latitude &&
                longitude == other.longitude &&
                mapAccuracyMeters == other.mapAccuracyMeters
        )

private val MapHorizontalContentPadding = 8.dp
private val MapVerticalContentPadding = 176.dp
private const val InitialZoomLevel = 17f
private const val CurrentLocationZoomLevel = 17f
private const val CameraAnimationMillis = 700
private const val CameraCoordinateTolerance = 0.00001
private const val CurrentLocationRefinementDurationMillis = 4_000L
private const val NanosPerMillisecond = 1_000_000L
private const val CurrentLocationPermissionError = "현재 위치를 사용하려면 위치 권한이 필요합니다."
private const val CurrentLocationUnavailableError = "현재 위치를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요."
private const val CurrentLocationMoveError = "현재 위치로 지도를 이동하지 못했습니다."
private const val MapCameraMoveError = "지도를 이동하지 못했습니다. 잠시 후 다시 시도해 주세요."

private val DestinationLocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
