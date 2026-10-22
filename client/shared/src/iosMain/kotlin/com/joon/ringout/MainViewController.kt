package com.joon.ringout

import androidx.compose.runtime.Composable
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.joon.ringout.alarm.createIosAlarmRuntime
import com.joon.ringout.di.IosAppContainer
import com.joon.ringout.di.AppContainer
import com.joon.ringout.domain.push.RoomActivityPushRequest
import com.joon.ringout.domain.push.RoomActivityPushTarget
import com.joon.ringout.presentation.ringing.AlarmRingingUiState
import com.joon.ringout.platform.IosRoomActivityPushListener
import com.joon.ringout.platform.IosNativeServices
import com.joon.ringout.platform.LocalIosNativeServices
import platform.Foundation.NSBundle
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

fun MainViewController(
    nativeServices: IosNativeServices,
    ): UIViewController = MainViewControllerWithContent(nativeServices) { _, content -> content(null) }

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
fun MainViewControllerWithContent(
    nativeServices: IosNativeServices,
    appHost: @Composable (AppContainer, @Composable ((@Composable (ThemeMode) -> Unit)?) -> Unit) -> Unit,
): UIViewController {
    val appContainer = IosAppContainer(nativeServices)

    return ComposeUIViewController {
        var pendingRoomActivityPush by remember(nativeServices) {
            mutableStateOf<RoomActivityPushRequest?>(null)
        }
        val roomActivityPushListener = remember(nativeServices) {
            object : IosRoomActivityPushListener {
                override fun onRoomActivityPushRequest(
                    requestId: String,
                    roomId: String?,
                    activityDate: String?,
                ) {
                    val target = if (roomId != null && activityDate != null) {
                        RoomActivityPushTarget.parse(
                            mapOf(
                                RoomActivityPushTarget.RoomIdKey to roomId,
                                RoomActivityPushTarget.ActivityDateKey to activityDate,
                            ),
                        )
                    } else {
                        null
                    }
                    pendingRoomActivityPush = target?.let { RoomActivityPushRequest(requestId, it) }
                    if (target == null) {
                        nativeServices.acknowledgeRoomActivityPush(requestId)
                    }
                }
            }
        }
        DisposableEffect(nativeServices, roomActivityPushListener) {
            val observerId = nativeServices.observeRoomActivityPush(roomActivityPushListener)
            onDispose { nativeServices.removeRoomActivityPushObserver(observerId) }
        }
        val alarmRuntime = remember(nativeServices) {
            createIosAlarmRuntime(nativeServices)
        }
        val ringingAlarm by alarmRuntime.ringingAlarmFlow.collectAsState()
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
        val ringingAlarmUiState = ringingAlarm?.let { alarm ->
            AlarmRingingUiState(
                id = alarm.systemAlarmId,
                limitMinutes = alarm.limitMinutes,
                destinationName = alarm.destinationName,
            )
        }
        CompositionLocalProvider(LocalIosNativeServices provides nativeServices) {
            appHost(appContainer) { extraContent ->
                App(
                    appContainer = appContainer,
                    appVersion = appVersion,
                    myPageExtraContent = extraContent,
                    useSystemLocationPermissionUiOnly = true,
                    ringingAlarm = ringingAlarmUiState,
                    pendingRoomActivityPush = pendingRoomActivityPush,
                    onRoomActivityPushHandled = { requestId ->
                        nativeServices.acknowledgeRoomActivityPush(requestId)
                        if (pendingRoomActivityPush?.requestId == requestId) {
                            pendingRoomActivityPush = null
                        }
                    },
                    activeAlarmMission = activeAlarmMission,
                    activeAlarmMissionLocation = activeAlarmMissionLocation,
                    missionLocationState = missionLocationState,
                    onRequestWhenInUseLocation = alarmRuntime::requestWhenInUseAuthorization,
                    onRequestAlwaysLocation = alarmRuntime::requestAlwaysAuthorization,
                    onConfirmAlwaysLocationResult = alarmRuntime::confirmAlwaysAuthorizationResult,
                    onRequestTemporaryFullAccuracy =
                        alarmRuntime::requestTemporaryFullAccuracyAuthorization,
                    onRingingAlarmDismiss = { systemAlarmId ->
                        coroutineScope.launch {
                            alarmRuntime.dismissRingingAlarm(systemAlarmId)
                        }
                    },
                    onActiveAlarmMissionExpired = {
                        coroutineScope.launch { alarmRuntime.handleDeadline() }
                    },
                    onActiveAlarmMissionForceEnd = { occurrenceId ->
                        alarmRuntime.requestForceEndActiveMission(occurrenceId)
                    },
                    onActiveAlarmMissionForceEndHoldStarted =
                        alarmRuntime::recordForceEndHoldStarted,
                    onActiveAlarmMissionForceEndHoldCancelled =
                        alarmRuntime::recordForceEndHoldCancelled,
                    onActiveAlarmMissionForceEndHoldCompleted =
                        alarmRuntime::recordForceEndHoldCompleted,
                )
            }
        }
    }
}
