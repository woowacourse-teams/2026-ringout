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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraPosition
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
    val currentOnCameraMoveStarted = rememberUpdatedState(onCameraMoveStarted)
    val currentOnCameraIdle = rememberUpdatedState(onCameraIdle)
    val currentOnCurrentLocationLoadingChange = rememberUpdatedState(onCurrentLocationLoadingChange)
    val currentOnCurrentLocationError = rememberUpdatedState(onCurrentLocationError)
    val currentOnMapError = rememberUpdatedState(onMapError)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val requestSerial = remember { AtomicInteger() }
    val reverseGeocoder = remember(context) { KakaoReverseGeocoder(context.applicationContext) }
    val currentLocationRequester = remember(context) {
        DestinationCurrentLocationRequester(context.applicationContext)
    }
    val mapView = remember(context, initialLatitude, initialLongitude) { MapView(context) }
    val addressExecutor = remember(mapView) { Executors.newSingleThreadExecutor() }
    var activeKakaoMap by remember(mapView) { mutableStateOf<KakaoMap?>(null) }
    var activeCurrentLocationRequestId by remember { mutableStateOf<Int?>(null) }
    var lastHandledCurrentLocationRequestId by remember { mutableStateOf(0) }
    val currentActiveKakaoMap = rememberUpdatedState(activeKakaoMap)

    fun cancelCurrentLocationRequest() {
        activeCurrentLocationRequestId = null
        currentLocationRequester.cancel()
        currentOnCurrentLocationLoadingChange.value(false)
    }

    fun requestAndMoveToCurrentLocation(requestId: Int) {
        if (activeCurrentLocationRequestId != requestId) return

        val kakaoMap = currentActiveKakaoMap.value
        if (kakaoMap == null) {
            activeCurrentLocationRequestId = null
            currentOnCurrentLocationLoadingChange.value(false)
            currentOnCurrentLocationError.value(CURRENT_LOCATION_UNAVAILABLE_ERROR)
            return
        }

        currentOnCurrentLocationLoadingChange.value(true)
        currentLocationRequester.request(
            onLocation = { location ->
                if (activeCurrentLocationRequestId != requestId) return@request
                activeCurrentLocationRequestId = null
                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    currentOnCurrentLocationLoadingChange.value(false)
                    return@request
                }
                runCatching {
                    kakaoMap.moveCamera(
                        CameraUpdateFactory.newCenterPosition(
                            LatLng.from(location.latitude, location.longitude),
                            17,
                        ),
                        CameraAnimation.from(700),
                    )
                }.onSuccess {
                    currentOnCurrentLocationLoadingChange.value(false)
                }.onFailure {
                    currentOnCurrentLocationLoadingChange.value(false)
                    currentOnCurrentLocationError.value(CURRENT_LOCATION_MOVE_ERROR)
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
            currentOnCurrentLocationError.value(CURRENT_LOCATION_PERMISSION_ERROR)
        }
    }

    LaunchedEffect(cameraTarget, activeKakaoMap) {
        val target = cameraTarget ?: return@LaunchedEffect
        cancelCurrentLocationRequest()
        activeKakaoMap?.moveCamera(
            CameraUpdateFactory.newCenterPosition(LatLng.from(target.latitude, target.longitude), 17),
            CameraAnimation.from(700),
        )
    }

    LaunchedEffect(currentLocationRequestId, activeKakaoMap) {
        if (
            currentLocationRequestId <= lastHandledCurrentLocationRequestId ||
            activeKakaoMap == null
        ) {
            return@LaunchedEffect
        }

        lastHandledCurrentLocationRequestId = currentLocationRequestId
        activeCurrentLocationRequestId = currentLocationRequestId
        currentOnCurrentLocationLoadingChange.value(true)
        if (context.hasDestinationLocationPermission()) {
            requestAndMoveToCurrentLocation(currentLocationRequestId)
        } else {
            locationPermissionLauncher.launch(destinationLocationPermissions)
        }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        var isFinished = false
        var addressRequest: java.util.concurrent.Future<*>? = null
        val isDisposed = AtomicBoolean(false)

        fun finishMap() {
            if (!isFinished) {
                isFinished = true
                mapView.finish()
            }
        }

        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() = Unit

                override fun onMapError(error: Exception) {
                    if (isDisposed.get()) return
                    mainHandler.post {
                        if (!isDisposed.get()) {
                            cancelCurrentLocationRequest()
                            currentOnMapError.value(error.message ?: "카카오 지도 인증에 실패했습니다.")
                        }
                    }
                }
            },
            object : KakaoMapReadyCallback() {
                override fun getPosition(): LatLng = LatLng.from(initialLatitude, initialLongitude)

                override fun getZoomLevel(): Int = 17

                override fun onMapReady(kakaoMap: KakaoMap) {
                    if (isDisposed.get()) return
                    activeKakaoMap = kakaoMap
                    kakaoMap.setOnCameraMoveStartListener(
                        object : KakaoMap.OnCameraMoveStartListener {
                            override fun onCameraMoveStart(
                                kakaoMap: KakaoMap,
                                gestureType: GestureType,
                            ) {
                                if (isDisposed.get()) return
                                cancelCurrentLocationRequest()
                                requestSerial.incrementAndGet()
                                addressRequest?.cancel(true)
                                currentOnCameraMoveStarted.value()
                            }
                        },
                    )
                    kakaoMap.setOnCameraMoveEndListener(
                        object : KakaoMap.OnCameraMoveEndListener {
                            override fun onCameraMoveEnd(
                                kakaoMap: KakaoMap,
                                cameraPosition: CameraPosition,
                                gestureType: GestureType,
                            ) {
                                if (isDisposed.get()) return
                                val center = cameraPosition.position
                                val serial = requestSerial.incrementAndGet()
                                addressRequest?.cancel(true)
                                try {
                                    addressRequest = addressExecutor.submit {
                                        val resolved = reverseGeocoder.resolve(
                                            latitude = center.latitude,
                                            longitude = center.longitude,
                                        )
                                        mainHandler.post {
                                            if (!isDisposed.get() && serial == requestSerial.get()) {
                                                currentOnCameraIdle.value(
                                                    center.latitude,
                                                    center.longitude,
                                                    resolved?.placeName,
                                                    resolved?.address,
                                                )
                                            }
                                        }
                                    }
                                } catch (_: RejectedExecutionException) {
                                    // The map left composition while the camera callback was being delivered.
                                }
                            }
                        },
                    )
                }
            },
        )
        mapView.setFinishManually(true)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                Lifecycle.Event.ON_STOP -> cancelCurrentLocationRequest()
                Lifecycle.Event.ON_DESTROY -> finishMap()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.resume()
        }

        onDispose {
            isDisposed.set(true)
            lifecycleOwner.lifecycle.removeObserver(observer)
            requestSerial.incrementAndGet()
            addressRequest?.cancel(true)
            addressExecutor.shutdownNow()
            cancelCurrentLocationRequest()
            finishMap()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
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
    val context = LocalContext.current
    val currentOnLoadingChange = rememberUpdatedState(onLoadingChange)
    val currentOnResults = rememberUpdatedState(onResults)
    val currentOnError = rememberUpdatedState(onError)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val searcher = remember(context) { KakaoDestinationSearcher(context.applicationContext) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdownNow() }
    }
    DisposableEffect(query, requestId) {
        if (query.isNullOrBlank()) return@DisposableEffect onDispose { }
        val isActive = AtomicBoolean(true)
        currentOnLoadingChange.value(true)
        val request = executor.submit {
            try {
                val results = searcher.search(query)
                mainHandler.post {
                    if (isActive.get()) {
                        currentOnLoadingChange.value(false)
                        currentOnResults.value(results)
                    }
                }
            } catch (_: InterruptedException) {
                // A newer query replaced this request.
            } catch (_: Exception) {
                mainHandler.post {
                    if (isActive.get()) {
                        currentOnLoadingChange.value(false)
                        currentOnError.value("검색 중 오류가 발생했습니다.")
                    }
                }
            }
        }
        onDispose {
            isActive.set(false)
            request.cancel(true)
        }
    }
}

private class KakaoDestinationSearcher(context: Context) {
    @Suppress("DEPRECATION")
    private val restApiKey = context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData
        ?.getString(REST_API_KEY_METADATA_NAME)
        .orEmpty()

    fun search(query: String): List<DestinationSelection> {
        if (restApiKey.isBlank()) throw IllegalStateException("REST API key is missing")
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val addressResults = request(
            "https://dapi.kakao.com/v2/local/search/address.json?query=$encodedQuery&size=10",
            isAddressSearch = true,
        )
        val keywordResults = request(
            "https://dapi.kakao.com/v2/local/search/keyword.json?query=$encodedQuery&size=15",
            isAddressSearch = false,
        )
        return (addressResults + keywordResults).distinctBy { "${it.latitude},${it.longitude}" }
    }

    private fun request(url: String, isAddressSearch: Boolean): List<DestinationSelection> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Authorization", "KakaoAK $restApiKey")
        }
        return try {
            if (connection.responseCode !in 200..299) return emptyList()
            val documents = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .optJSONArray("documents")
                ?: return emptyList()
            buildList {
                for (index in 0 until documents.length()) {
                    val item = documents.optJSONObject(index) ?: continue
                    val longitude = item.optString("x").toDoubleOrNull() ?: continue
                    val latitude = item.optString("y").toDoubleOrNull() ?: continue
                    val roadAddress = item.optString("road_address_name")
                    val lotAddress = item.optString("address_name")
                    val address = roadAddress.ifBlank { lotAddress }
                    val name = if (isAddressSearch) {
                        item.optJSONObject("road_address")?.optString("building_name").orEmpty()
                            .ifBlank { address }
                    } else {
                        item.optString("place_name").ifBlank { address }
                    }
                    if (address.isNotBlank()) {
                        add(DestinationSelection(name, address, latitude, longitude))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

private class KakaoReverseGeocoder(context: Context) {
    @Suppress("DEPRECATION")
    private val restApiKey = context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData
        ?.getString(REST_API_KEY_METADATA_NAME)
        .orEmpty()

    fun resolve(latitude: Double, longitude: Double): ResolvedAddress? {
        if (restApiKey.isBlank()) return null

        val addressDocument = requestFirstDocument(
            "https://dapi.kakao.com/v2/local/geo/coord2address.json" +
                "?x=$longitude&y=$latitude&input_coord=WGS84",
        )
        if (addressDocument != null) {
            val roadAddress = addressDocument.optJSONObject("road_address")
            val lotAddress = addressDocument.optJSONObject("address")
            val address = roadAddress?.optString("address_name").orEmpty()
                .ifBlank { roadAddress?.optString("road_name").orEmpty() }
                .ifBlank { lotAddress?.optString("address_name").orEmpty() }
            if (address.isNotBlank()) {
                return ResolvedAddress(
                    placeName = roadAddress
                        ?.optString("building_name")
                        .orEmpty()
                        .takeIf(String::isNotBlank),
                    address = address,
                )
            }
        }

        val regionDocuments = requestDocuments(
            "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json" +
                "?x=$longitude&y=$latitude&input_coord=WGS84",
        )
        val regionDocument = regionDocuments.firstOrNull {
            it.optString("region_type") == "H"
        } ?: regionDocuments.firstOrNull()
        val regionAddress = regionDocument?.optString("address_name").orEmpty()
        return regionAddress.takeIf(String::isNotBlank)?.let {
            ResolvedAddress(placeName = null, address = it)
        }
    }

    private fun requestFirstDocument(url: String): JSONObject? =
        requestDocuments(url).firstOrNull()

    private fun requestDocuments(url: String): List<JSONObject> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Authorization", "KakaoAK $restApiKey")
        }

        return try {
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val documents = JSONObject(body)
                .optJSONArray("documents")
                ?: return emptyList()
            buildList {
                for (index in 0 until documents.length()) {
                    documents.optJSONObject(index)?.let(::add)
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

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
            onError(CURRENT_LOCATION_PERMISSION_ERROR)
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
            onError(CURRENT_LOCATION_SERVICES_ERROR)
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
                onError(CURRENT_LOCATION_UNAVAILABLE_ERROR)
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
            mainHandler.postDelayed(timeout, CURRENT_LOCATION_TIMEOUT_MILLIS)
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
        time > 0L && ageMillis in -CURRENT_LOCATION_CLOCK_SKEW_MILLIS..CURRENT_LOCATION_MAX_AGE_MILLIS
}

private val Location.mapAccuracyMeters: Float
    get() = accuracy.takeIf { hasAccuracy() && it.isFinite() && it >= 0f }
        ?: Float.POSITIVE_INFINITY

private fun Location.isPreciseEnoughForMap(): Boolean =
    mapAccuracyMeters <= CURRENT_LOCATION_TARGET_ACCURACY_METERS

private fun Location.isBetterForMapThan(other: Location?): Boolean =
    other == null ||
        mapAccuracyMeters < other.mapAccuracyMeters ||
        (mapAccuracyMeters == other.mapAccuracyMeters && time > other.time)

private const val REST_API_KEY_METADATA_NAME = "com.joon.ringout.KAKAO_REST_API_KEY"
private const val CURRENT_LOCATION_TIMEOUT_MILLIS = 8_000L
private const val CURRENT_LOCATION_MAX_AGE_MILLIS = 30_000L
private const val CURRENT_LOCATION_CLOCK_SKEW_MILLIS = 5_000L
private const val CURRENT_LOCATION_TARGET_ACCURACY_METERS = 50f
private const val CURRENT_LOCATION_PERMISSION_ERROR = "현재 위치를 사용하려면 위치 권한이 필요합니다."
private const val CURRENT_LOCATION_SERVICES_ERROR = "기기의 위치 서비스를 켜 주세요."
private const val CURRENT_LOCATION_UNAVAILABLE_ERROR = "현재 위치를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요."
private const val CURRENT_LOCATION_MOVE_ERROR = "현재 위치로 지도를 이동하지 못했습니다."

private val destinationLocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private data class ResolvedAddress(
    val placeName: String?,
    val address: String,
)
