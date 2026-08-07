package com.joon.ringout.presentation.destination

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
actual fun PlatformDestinationMap(
    initialLatitude: Double,
    initialLongitude: Double,
    cameraTarget: DestinationSelection?,
    currentLocationRequestId: Int,
    onCameraMoveStarted: () -> Unit,
    onCameraIdle: (
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
    val currentOnCameraIdle = rememberUpdatedState(onCameraIdle)
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

    fun cancelCurrentLocationRequest() {
        activeCurrentLocationRequestId = null
        currentLocationRequester.cancel()
        currentOnCurrentLocationLoadingChange.value(false)
    }

    fun requestAndMoveToCurrentLocation(requestId: Int) {
        if (activeCurrentLocationRequestId != requestId) return

        currentOnCurrentLocationLoadingChange.value(true)
        currentLocationRequester.request(
            onLocation = { location ->
                if (activeCurrentLocationRequestId != requestId) return@request
                activeCurrentLocationRequestId = null
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    currentOnCurrentLocationLoadingChange.value(false)
                    return@request
                }
                coroutineScope.launch {
                    try {
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(
                                LatLng(location.latitude, location.longitude),
                                CurrentLocationZoomLevel,
                            ),
                            durationMs = CameraAnimationMillis,
                        )
                        currentOnCurrentLocationLoadingChange.value(false)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        currentOnCurrentLocationLoadingChange.value(false)
                        currentOnCurrentLocationError.value(CurrentLocationMoveError)
                    }
                }
            },
            onError = { error ->
                if (activeCurrentLocationRequestId != requestId) return@request
                activeCurrentLocationRequestId = null
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
        activeCurrentLocationRequestId = currentLocationRequestId
        currentOnCurrentLocationLoadingChange.value(true)
        if (context.hasDestinationLocationPermission()) {
            requestAndMoveToCurrentLocation(currentLocationRequestId)
        } else {
            locationPermissionLauncher.launch(DestinationLocationPermissions)
        }
    }

    LaunchedEffect(cameraPositionState) {
        var hasObservedCameraMovement = false
        var reverseGeocodeJob: Job? = null
        try {
            snapshotFlow { cameraPositionState.isMoving }
                .distinctUntilChanged()
                .collect { isMoving ->
                    if (isMoving) {
                        hasObservedCameraMovement = true
                        reverseGeocodeJob?.cancel()
                        cancelCurrentLocationRequest()
                        currentOnCameraMoveStarted.value()
                    } else if (hasObservedCameraMovement) {
                        val center = cameraPositionState.position.target
                        reverseGeocodeJob?.cancel()
                        reverseGeocodeJob = launch {
                            val resolved = reverseGeocoder.resolve(
                                latitude = center.latitude,
                                longitude = center.longitude,
                            )
                            currentOnCameraIdle.value(
                                center.latitude,
                                center.longitude,
                                resolved?.placeName,
                                resolved?.address,
                            )
                        }
                    }
                }
        } finally {
            reverseGeocodeJob?.cancel()
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

private class DestinationCurrentLocationRequester(context: Context) {
    private val applicationContext = context.applicationContext
    private val locationManager = applicationContext.getSystemService(LocationManager::class.java)
    private val mainLooper = Looper.getMainLooper()
    private val mainHandler = Handler(mainLooper)
    private var activeLocationListener: LocationListener? = null
    private var activeTimeout: Runnable? = null

    fun request(
        onLocation: (Location) -> Unit,
        onError: (String) -> Unit,
    ) {
        cancel()

        if (!applicationContext.hasDestinationLocationPermission()) {
            onError(CurrentLocationPermissionError)
            return
        }

        val enabledProviders = runCatching {
            locationManager.getProviders(true)
        }.getOrDefault(emptyList())
        val providers = buildList {
            if (
                applicationContext.hasDestinationFineLocationPermission() &&
                LocationManager.GPS_PROVIDER in enabledProviders
            ) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (LocationManager.NETWORK_PROVIDER in enabledProviders) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        if (providers.isEmpty()) {
            onError(CurrentLocationServicesError)
            return
        }

        val recentLocation = providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { location -> location.isUsableForMap() }
            .minWithOrNull(
                compareBy<Location> { location -> location.mapAccuracyMeters }
                    .thenByDescending { location -> location.time },
            )
        if (recentLocation?.isPreciseEnoughForMap() == true) {
            onLocation(recentLocation)
            return
        }

        var isCompleted = false
        var bestLocation = recentLocation
        lateinit var locationListener: LocationListener
        lateinit var timeout: Runnable

        fun complete(location: Location?) {
            if (isCompleted || activeLocationListener !== locationListener) return
            isCompleted = true
            cancel()
            if (location != null) {
                onLocation(location)
            } else {
                onError(CurrentLocationUnavailableError)
            }
        }

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!location.isUsableForMap()) return
                if (location.isBetterForMapThan(bestLocation)) {
                    bestLocation = location
                }
                if (location.isPreciseEnoughForMap()) {
                    complete(location)
                }
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?,
            ) = Unit
        }
        timeout = Runnable { complete(bestLocation) }
        activeLocationListener = locationListener
        activeTimeout = timeout

        val registeredProviderCount = providers.count { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    locationListener,
                    mainLooper,
                )
            }.isSuccess
        }
        if (registeredProviderCount == 0) {
            complete(bestLocation)
        } else {
            mainHandler.postDelayed(timeout, CurrentLocationTimeoutMillis)
        }
    }

    fun cancel() {
        activeTimeout?.let(mainHandler::removeCallbacks)
        activeTimeout = null
        activeLocationListener?.let { listener ->
            runCatching { locationManager.removeUpdates(listener) }
        }
        activeLocationListener = null
    }
}

private fun Context.hasDestinationFineLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.hasDestinationLocationPermission(): Boolean =
    hasDestinationFineLocationPermission() ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Location.isUsableForMap(): Boolean {
    val ageMillis = System.currentTimeMillis() - time
    return latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 &&
        time > 0L && ageMillis in -CurrentLocationClockSkewMillis..CurrentLocationMaxAgeMillis
}

private val Location.mapAccuracyMeters: Float
    get() = accuracy.takeIf { hasAccuracy() && it.isFinite() && it >= 0f }
        ?: Float.POSITIVE_INFINITY

private fun Location.isPreciseEnoughForMap(): Boolean =
    mapAccuracyMeters <= CurrentLocationTargetAccuracyMeters

private fun Location.isBetterForMapThan(other: Location?): Boolean =
    other == null ||
        mapAccuracyMeters < other.mapAccuracyMeters ||
        (mapAccuracyMeters == other.mapAccuracyMeters && time > other.time)

private val MapHorizontalContentPadding = 8.dp
private val MapVerticalContentPadding = 176.dp
private const val InitialZoomLevel = 17f
private const val CurrentLocationZoomLevel = 17f
private const val CameraAnimationMillis = 700
private const val CurrentLocationTimeoutMillis = 8_000L
private const val CurrentLocationMaxAgeMillis = 30_000L
private const val CurrentLocationClockSkewMillis = 5_000L
private const val CurrentLocationTargetAccuracyMeters = 50f
private const val CurrentLocationPermissionError = "현재 위치를 사용하려면 위치 권한이 필요합니다."
private const val CurrentLocationServicesError = "기기의 위치 서비스를 켜 주세요."
private const val CurrentLocationUnavailableError = "현재 위치를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요."
private const val CurrentLocationMoveError = "현재 위치로 지도를 이동하지 못했습니다."
private const val MapCameraMoveError = "지도를 이동하지 못했습니다. 잠시 후 다시 시도해 주세요."

private val DestinationLocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
