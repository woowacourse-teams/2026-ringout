package com.joon.ringout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.joon.ringout.alarm.createIosAlarmRuntime
import com.joon.ringout.platform.IosNativeServices
import com.joon.ringout.platform.LocalIosNativeServices
import platform.Foundation.NSBundle
import kotlinx.coroutines.launch

fun MainViewController(nativeServices: IosNativeServices) = ComposeUIViewController {
    val alarmRuntime = remember(nativeServices) {
        createIosAlarmRuntime(nativeServices)
    }
    val activeAlarmMission by alarmRuntime.activeMissionFlow.collectAsState()
    val activeAlarmMissionLocation by alarmRuntime.currentLocationFlow.collectAsState()
    val missionLocationState by alarmRuntime.locationStateFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(alarmRuntime) {
        alarmRuntime.start()
    }
    DisposableEffect(lifecycleOwner, alarmRuntime) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch { alarmRuntime.start() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val appVersion = NSBundle.mainBundle
        .objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        ?: ""
    CompositionLocalProvider(LocalIosNativeServices provides nativeServices) {
        App(
            appVersion = appVersion,
            activeAlarmMission = activeAlarmMission,
            activeAlarmMissionLocation = activeAlarmMissionLocation,
            missionLocationState = missionLocationState,
            onRequestWhenInUseLocation = alarmRuntime::requestWhenInUseAuthorization,
            onRequestAlwaysLocation = alarmRuntime::requestAlwaysAuthorization,
            onConfirmAlwaysLocationResult = alarmRuntime::confirmAlwaysAuthorizationResult,
            onRequestTemporaryFullAccuracy =
                alarmRuntime::requestTemporaryFullAccuracyAuthorization,
            onActiveAlarmMissionExpired = {
                coroutineScope.launch { alarmRuntime.handleDeadline() }
            },
            onActiveAlarmMissionForceEnd = { occurrenceId ->
                coroutineScope.launch { alarmRuntime.forceEndActiveMission(occurrenceId) }
            },
        )
    }
}
