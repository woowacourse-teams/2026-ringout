package com.joon.ringout.presentation.roomhome

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
internal actual fun RoomHomeSystemBars(useDarkStatusIcons: Boolean, useDarkNavigationIcons: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = context.roomHomeActivity() ?: return
    val controller = remember(activity, view) { WindowCompat.getInsetsController(activity.window, view) }
    DisposableEffect(controller) {
        val originalStatus = controller.isAppearanceLightStatusBars
        val originalNavigation = controller.isAppearanceLightNavigationBars
        onDispose {
            controller.isAppearanceLightStatusBars = originalStatus
            controller.isAppearanceLightNavigationBars = originalNavigation
        }
    }
    SideEffect {
        controller.isAppearanceLightStatusBars = useDarkStatusIcons
        controller.isAppearanceLightNavigationBars = useDarkNavigationIcons
    }
}

private tailrec fun Context.roomHomeActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.roomHomeActivity()
    else -> null
}
