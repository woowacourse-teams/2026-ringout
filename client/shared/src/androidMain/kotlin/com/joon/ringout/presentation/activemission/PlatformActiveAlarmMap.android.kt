package com.joon.ringout.presentation.activemission

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import java.util.concurrent.atomic.AtomicBoolean
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMapError = rememberUpdatedState(onMapError)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val destinationPosition = remember(destinationLatitude, destinationLongitude) {
        LatLng.from(destinationLatitude, destinationLongitude)
    }
    val mapView = remember(context, destinationLatitude, destinationLongitude) {
        MapView(context)
    }
    val markerDensity = context.resources.displayMetrics.density
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
    var activeKakaoMap by remember(mapView) { mutableStateOf<KakaoMap?>(null) }
    var hasFittedBothLocations by remember(mapView) { mutableStateOf(false) }

    LaunchedEffect(
        activeKakaoMap,
        destinationPosition,
        destinationMarker,
    ) {
        val kakaoMap = activeKakaoMap ?: return@LaunchedEffect
        val labelLayer = kakaoMap.activeAlarmLabelLayer() ?: return@LaunchedEffect
        labelLayer.getLabel(DestinationLabelId)?.remove()
        labelLayer.addLabel(
            LabelOptions
                .from(DestinationLabelId, destinationPosition)
                .setStyles(
                    LabelStyle
                        .from(destinationMarker)
                        .setAnchorPoint(0.5f, 1f),
                ),
        )
    }

    LaunchedEffect(
        activeKakaoMap,
        currentLatitude,
        currentLongitude,
        currentLocationMarker,
    ) {
        val kakaoMap = activeKakaoMap ?: return@LaunchedEffect
        val labelLayer = kakaoMap.activeAlarmLabelLayer() ?: return@LaunchedEffect
        if (currentLatitude == null || currentLongitude == null) {
            labelLayer.getLabel(CurrentLocationLabelId)?.remove()
            return@LaunchedEffect
        }

        val currentPosition = LatLng.from(currentLatitude, currentLongitude)
        val currentLabel = labelLayer.getLabel(CurrentLocationLabelId)
            ?: labelLayer.addLabel(
                LabelOptions
                    .from(CurrentLocationLabelId, currentPosition)
                    .setStyles(
                        LabelStyle
                            .from(currentLocationMarker)
                            .setAnchorPoint(0.5f, 0.5f),
                    ),
            )
        currentLabel.moveTo(currentPosition)

        if (!hasFittedBothLocations) {
            val horizontalPadding = (24f * markerDensity).roundToInt()
            val topPadding = (112f * markerDensity).roundToInt()
            val bottomPadding = (240f * markerDensity).roundToInt()
            val pointPadding = (32f * markerDensity).roundToInt()
            kakaoMap.setPadding(
                horizontalPadding,
                topPadding,
                horizontalPadding,
                bottomPadding,
            )
            val cameraUpdate = if (
                abs(currentLatitude - destinationLatitude) < SameLocationThreshold &&
                abs(currentLongitude - destinationLongitude) < SameLocationThreshold
            ) {
                CameraUpdateFactory.newCenterPosition(destinationPosition, CloseLocationZoomLevel)
            } else {
                CameraUpdateFactory.fitMapPoints(
                    arrayOf(destinationPosition, currentPosition),
                    pointPadding,
                )
            }
            kakaoMap.moveCamera(cameraUpdate, CameraAnimation.from(CameraAnimationMillis))
            hasFittedBothLocations = true
        }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        var isFinished = false
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
                    mainHandler.post {
                        if (!isDisposed.get()) {
                            currentOnMapError.value(
                                error.message ?: "카카오 지도를 불러올 수 없습니다.",
                            )
                        }
                    }
                }
            },
            object : KakaoMapReadyCallback() {
                override fun getPosition(): LatLng = destinationPosition

                override fun getZoomLevel(): Int = InitialZoomLevel

                override fun onMapReady(kakaoMap: KakaoMap) {
                    if (!isDisposed.get()) activeKakaoMap = kakaoMap
                }
            },
        )
        mapView.setFinishManually(true)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
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
            activeKakaoMap = null
            finishMap()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}

private fun KakaoMap.activeAlarmLabelLayer(): LabelLayer? {
    val manager = labelManager ?: return null
    return manager.getLayer(TrackingLabelLayerId)
        ?: manager.addLayer(
            LabelLayerOptions
                .from(TrackingLabelLayerId)
                .setZOrder(TrackingLabelLayerZOrder),
        )
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

private const val DestinationLabelId = "active-alarm-destination"
private const val CurrentLocationLabelId = "active-alarm-current-location"
private const val TrackingLabelLayerId = "active-alarm-tracking"
private const val TrackingLabelLayerZOrder = 10_000
private const val InitialZoomLevel = 16
private const val CloseLocationZoomLevel = 18
private const val CameraAnimationMillis = 700
private const val SameLocationThreshold = 0.00001
