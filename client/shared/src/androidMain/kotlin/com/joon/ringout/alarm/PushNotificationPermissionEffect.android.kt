package com.joon.ringout.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PushNotificationPermissionEffect(isEligible: Boolean) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The OS permission state is the source of truth. */ }

    LaunchedEffect(isEligible, context) {
        if (isEligible && NotificationPermissionPrompt.tryAcquire(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Shared by onboarding, Home and alarm setup; persist before launching. */
internal object NotificationPermissionPrompt {
    @Synchronized
    fun tryAcquire(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            markRequested(context)
            return false
        }
        if (hasRequested(context)) return false
        markRequested(context)
        return true
    }

    fun hasRequested(context: Context): Boolean = preferences(context).getBoolean(REQUESTED, false)

    fun markRequested(context: Context) {
        preferences(context).edit().putBoolean(REQUESTED, true).apply()
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        "ringout_permission_prompts",
        Context.MODE_PRIVATE,
    )

    private const val REQUESTED = "notification_permission_requested"
}
