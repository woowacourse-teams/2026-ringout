package com.joon.ringout.presentation.activemission

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.joon.ringout.presentation.activemission.components.ActiveAlarmCurrentLocationMarkerResource
import com.joon.ringout.presentation.activemission.components.ActiveAlarmDestinationMarkerResource
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

@Composable
actual fun PlatformActiveAlarmMap(
    destinationLatitude: Double,
    destinationLongitude: Double,
    currentLatitude: Double?,
    currentLongitude: Double?,
    onMapError: (String) -> Unit,
    modifier: Modifier,
) {
    key(destinationLatitude, destinationLongitude) {
        GoogleActiveAlarmMap(
            destinationPosition = LatLng(destinationLatitude, destinationLongitude),
            currentPosition = if (currentLatitude != null && currentLongitude != null) {
                LatLng(currentLatitude, currentLongitude)
            } else {
                null
            },
            onMapError = onMapError,
            modifier = modifier,
        )
    }
}

@Composable
private fun GoogleActiveAlarmMap(
    destinationPosition: LatLng,
    currentPosition: LatLng?,
    onMapError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnMapError = rememberUpdatedState(onMapError)
    val mapColorScheme = when (LocalRingoutThemeMode.current) {
        ThemeMode.Dark -> ComposeMapColorScheme.DARK
        ThemeMode.Light -> ComposeMapColorScheme.LIGHT
    }
    val pointPadding = with(LocalDensity.current) { MapPointPadding.roundToPx() }
    val destinationMarker = rememberMarkerBitmap(
        painter = painterResource(ActiveAlarmDestinationMarkerResource),
        width = DestinationMarkerWidth,
        height = DestinationMarkerHeight,
    )
    val currentLocationMarker = rememberMarkerBitmap(
        painter = painterResource(ActiveAlarmCurrentLocationMarkerResource),
        width = CurrentLocationMarkerWidth,
        height = CurrentLocationMarkerHeight,
    )
    val destinationMarkerState = remember {
        MarkerState(position = destinationPosition)
    }
    val currentLocationMarkerState = remember {
        MarkerState(position = currentPosition ?: destinationPosition)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(destinationPosition, InitialZoomLevel)
    }
    val uiSettings = remember {
        MapUiSettings(
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
        )
    }
    var isMapLoaded by remember { mutableStateOf(false) }
    var hasFittedBothLocations by remember { mutableStateOf(false) }
    val hasCurrentLocation = currentPosition != null

    LaunchedEffect(currentPosition) {
        if (currentPosition != null) {
            currentLocationMarkerState.position = currentPosition
        }
    }

    LaunchedEffect(isMapLoaded, hasCurrentLocation) {
        val location = currentPosition ?: return@LaunchedEffect
        if (!isMapLoaded || hasFittedBothLocations) return@LaunchedEffect

        try {
            val cameraUpdate = if (
                abs(location.latitude - destinationPosition.latitude) < SameLocationThreshold &&
                abs(location.longitude - destinationPosition.longitude) < SameLocationThreshold
            ) {
                CameraUpdateFactory.newLatLngZoom(destinationPosition, CloseLocationZoomLevel)
            } else {
                val bounds = LatLngBounds.builder()
                    .include(destinationPosition)
                    .include(location)
                    .build()
                CameraUpdateFactory.newLatLngBounds(bounds, pointPadding)
            }
            cameraPositionState.animate(
                update = cameraUpdate,
                durationMs = CameraAnimationMillis,
            )
            hasFittedBothLocations = true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            currentOnMapError.value(
                error.message ?: "지도를 표시할 수 없습니다.",
            )
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentDescription = "알람 목적지 추적 지도",
        contentPadding = MapContentPadding,
        mapColorScheme = mapColorScheme,
        uiSettings = uiSettings,
        onMapLoaded = { isMapLoaded = true },
    ) {
        val destinationIcon = remember(destinationMarker) {
            BitmapDescriptorFactory.fromBitmap(destinationMarker)
        }
        val currentLocationIcon = remember(currentLocationMarker) {
            BitmapDescriptorFactory.fromBitmap(currentLocationMarker)
        }

        Marker(
            state = destinationMarkerState,
            anchor = Offset(0.5f, 1f),
            icon = destinationIcon,
            zIndex = DestinationMarkerZIndex,
        )
        Marker(
            state = currentLocationMarkerState,
            anchor = Offset(0.5f, CurrentLocationMarkerAnchorY),
            icon = currentLocationIcon,
            visible = currentPosition != null,
            zIndex = CurrentLocationMarkerZIndex,
        )
    }
}

@Composable
private fun rememberMarkerBitmap(
    painter: Painter,
    width: Dp,
    height: Dp,
): Bitmap {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    return remember(painter, density, layoutDirection, width, height) {
        val widthPx = with(density) { width.roundToPx() }
        val heightPx = with(density) { height.roundToPx() }
        val imageBitmap = ImageBitmap(widthPx, heightPx)
        val canvas = Canvas(imageBitmap)
        val size = Size(widthPx.toFloat(), heightPx.toFloat())

        CanvasDrawScope().draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = canvas,
            size = size,
        ) {
            with(painter) { draw(size) }
        }
        imageBitmap.asAndroidBitmap()
    }
}

private val MapContentPadding = PaddingValues(
    start = 24.dp,
    top = 80.dp,
    end = 24.dp,
    bottom = 243.dp,
)
private val MapPointPadding = 32.dp
private val DestinationMarkerWidth = 45.dp
private val DestinationMarkerHeight = 52.5.dp
private val CurrentLocationMarkerWidth = 36.dp
private val CurrentLocationMarkerHeight = 38.dp
private const val InitialZoomLevel = 16f
private const val CloseLocationZoomLevel = 18f
private const val CameraAnimationMillis = 700
private const val SameLocationThreshold = 0.00001
private const val DestinationMarkerZIndex = 1f
private const val CurrentLocationMarkerZIndex = 2f
private const val CurrentLocationMarkerAnchorY = 18f / 38f
