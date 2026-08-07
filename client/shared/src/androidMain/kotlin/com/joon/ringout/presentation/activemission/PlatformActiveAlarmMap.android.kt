package com.joon.ringout.presentation.activemission

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val markerDensity = LocalDensity.current.density
    val pointPadding = with(LocalDensity.current) { MapPointPadding.roundToPx() }
    val destinationColor = MaterialTheme.colorScheme.primary.toArgb()
    val markerCutoutColor = MaterialTheme.colorScheme.surface.toArgb()
    val currentLocationColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val destinationMarker = remember(
        markerDensity,
        destinationColor,
        markerCutoutColor,
    ) {
        createDestinationMarker(
            density = markerDensity,
            fillColor = destinationColor,
            cutoutColor = markerCutoutColor,
        )
    }
    val currentLocationMarker = remember(
        markerDensity,
        markerCutoutColor,
        currentLocationColor,
    ) {
        createCurrentLocationMarker(
            density = markerDensity,
            borderColor = markerCutoutColor,
            fillColor = currentLocationColor,
        )
    }
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
            anchor = Offset(0.5f, 0.5f),
            icon = currentLocationIcon,
            visible = currentPosition != null,
            zIndex = CurrentLocationMarkerZIndex,
        )
    }
}

private fun createDestinationMarker(
    density: Float,
    fillColor: Int,
    cutoutColor: Int,
): Bitmap {
    val width = (44f * density).roundToInt()
    val height = (58f * density).roundToInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = 0x33000000
    canvas.drawOval(
        RectF(
            width * 0.18f,
            height * 0.84f,
            width * 0.82f,
            height * 0.98f,
        ),
        paint,
    )

    val pinPath = Path().apply {
        moveTo(width * 0.5f, height * 0.9f)
        cubicTo(
            width * 0.42f,
            height * 0.74f,
            width * 0.12f,
            height * 0.54f,
            width * 0.12f,
            height * 0.32f,
        )
        cubicTo(
            width * 0.12f,
            height * 0.11f,
            width * 0.29f,
            height * 0.03f,
            width * 0.5f,
            height * 0.03f,
        )
        cubicTo(
            width * 0.71f,
            height * 0.03f,
            width * 0.88f,
            height * 0.11f,
            width * 0.88f,
            height * 0.32f,
        )
        cubicTo(
            width * 0.88f,
            height * 0.54f,
            width * 0.58f,
            height * 0.74f,
            width * 0.5f,
            height * 0.9f,
        )
        close()
    }
    paint.color = fillColor
    canvas.drawPath(pinPath, paint)
    paint.color = cutoutColor
    canvas.drawCircle(width * 0.5f, height * 0.3f, width * 0.15f, paint)

    return bitmap
}

private fun createCurrentLocationMarker(
    density: Float,
    borderColor: Int,
    fillColor: Int,
): Bitmap {
    val size = (34f * density).roundToInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f

    paint.color = 0x33000000
    canvas.drawCircle(center, center + density, size * 0.48f, paint)
    paint.color = borderColor
    canvas.drawCircle(center, center, size * 0.46f, paint)
    paint.color = fillColor
    canvas.drawCircle(center, center, size * 0.31f, paint)

    return bitmap
}

private val MapContentPadding = PaddingValues(
    start = 24.dp,
    top = 112.dp,
    end = 24.dp,
    bottom = 240.dp,
)
private val MapPointPadding = 32.dp
private const val InitialZoomLevel = 16f
private const val CloseLocationZoomLevel = 18f
private const val CameraAnimationMillis = 700
private const val SameLocationThreshold = 0.00001
private const val DestinationMarkerZIndex = 1f
private const val CurrentLocationMarkerZIndex = 2f
