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

/** Shared by Home and alarm setup; persist before launching to cover concurrent callers. */
internal object NotificationPermissionPrompt {
    @Synchronized
    fun tryAcquire(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val preferences = context.applicationContext.getSharedPreferences(
            "ringout_permission_prompts",
            Context.MODE_PRIVATE,
        )
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            preferences.edit().putBoolean(REQUESTED, true).apply()
            return false
        }
        if (preferences.getBoolean(REQUESTED, false)) return false
        preferences.edit().putBoolean(REQUESTED, true).apply()
        return true
    }

    private const val REQUESTED = "notification_permission_requested"
}
