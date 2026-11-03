package com.joon.ringout.domain.update

enum class AppUpdateAccess {
    Checking,
    Allowed,
    Required,
    DeferredForAlarm,
}

/** Alarms may finish while config is loading or an update is required; general app use may not. */
fun evaluateAppUpdateAccess(
    currentVersionCode: Int,
    minVersionCode: Int,
    isConfigInitialized: Boolean,
    isAlarmInProgress: Boolean,
): AppUpdateAccess = when {
    !isConfigInitialized && isAlarmInProgress -> AppUpdateAccess.DeferredForAlarm
    !isConfigInitialized -> AppUpdateAccess.Checking
    currentVersionCode >= minVersionCode -> AppUpdateAccess.Allowed
    isAlarmInProgress -> AppUpdateAccess.DeferredForAlarm
    else -> AppUpdateAccess.Required
}
