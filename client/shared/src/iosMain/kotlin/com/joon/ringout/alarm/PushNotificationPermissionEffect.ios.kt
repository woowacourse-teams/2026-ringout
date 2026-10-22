package com.joon.ringout.alarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.joon.ringout.platform.LocalIosNativeServices

@Composable
actual fun PushNotificationPermissionEffect(isEligible: Boolean) {
    val nativeServices = LocalIosNativeServices.current
    LaunchedEffect(isEligible, nativeServices) {
        if (isEligible) {
            nativeServices.requestNotificationPermissionIfNeeded()
        }
    }
}
