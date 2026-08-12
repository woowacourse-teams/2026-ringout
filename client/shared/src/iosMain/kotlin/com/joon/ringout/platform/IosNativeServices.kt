package com.joon.ringout.platform

import androidx.compose.runtime.staticCompositionLocalOf

enum class IosAlarmAuthorizationState {
    NOT_DETERMINED,
    DENIED,
    AUTHORIZED,
}

interface IosNativeServices {
    fun isMapsAvailable(): Boolean

    fun isPlacesAvailable(): Boolean

    fun alarmAuthorizationState(): IosAlarmAuthorizationState

    fun normalizeAlarmId(id: String): String?
}

val LocalIosNativeServices = staticCompositionLocalOf<IosNativeServices> {
    error("IosNativeServices is not available outside the iOS app entry point.")
}
