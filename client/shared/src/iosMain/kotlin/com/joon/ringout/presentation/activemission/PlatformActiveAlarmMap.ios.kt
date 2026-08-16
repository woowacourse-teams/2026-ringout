package com.joon.ringout.presentation.activemission

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import com.joon.ringout.platform.LocalIosNativeServices

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun PlatformActiveAlarmMap(
    destinationLatitude: Double,
    destinationLongitude: Double,
    currentLatitude: Double?,
    currentLongitude: Double?,
    onMapError: (String) -> Unit,
    modifier: Modifier,
) {
    val nativeServices = LocalIosNativeServices.current
    val isDarkMode = LocalRingoutThemeMode.current == ThemeMode.Dark
    val controller = remember(
        nativeServices,
        destinationLatitude,
        destinationLongitude,
    ) {
        nativeServices.createActiveMissionMapController(
            destinationLatitude = destinationLatitude,
            destinationLongitude = destinationLongitude,
        )
    }
    if (controller == null) {
        LaunchedEffect(Unit) { onMapError("지도를 표시할 수 없습니다.") }
        return
    }

    LaunchedEffect(controller, isDarkMode) {
        controller.setDarkModeEnabled(isDarkMode)
    }
    LaunchedEffect(controller, currentLatitude, currentLongitude) {
        if (currentLatitude != null && currentLongitude != null) {
            controller.updateCurrentLocation(currentLatitude, currentLongitude)
        } else {
            controller.clearCurrentLocation()
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }
    UIKitView(
        factory = controller::view,
        modifier = modifier.fillMaxSize(),
        onRelease = { controller.dispose() },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = false,
        ),
    )
}
