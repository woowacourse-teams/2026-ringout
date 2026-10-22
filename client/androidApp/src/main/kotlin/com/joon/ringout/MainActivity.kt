package com.joon.ringout

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.ActiveAlarmMissionStore
import com.joon.ringout.alarm.ForceEndMissionResult
import com.joon.ringout.alarm.AlarmMissionCoordinator
import com.joon.ringout.presentation.update.AppUpdateDialog
import com.joon.ringout.presentation.update.PlayStoreUpdateChecker
import com.joon.ringout.push.RoomActivityPushClickViewModel

class MainActivity : ComponentActivity() {
    private data class IncomingIntent(
        val intent: Intent,
        val isNewIntent: Boolean,
    )

    private var incomingIntent by mutableStateOf<IncomingIntent?>(null)
    private lateinit var activeAlarmMissionStore: ActiveAlarmMissionStore
    private lateinit var alarmMissionCoordinator: AlarmMissionCoordinator
    private var activeAlarmMission by mutableStateOf<ActiveAlarmMission?>(null)
    private var activeAlarmMissionLocation by mutableStateOf<ActiveAlarmMissionLocation?>(null)
    private var isUpdateDialogVisible by mutableStateOf(false)
    private lateinit var playStoreUpdateChecker: PlayStoreUpdateChecker
    private val activeAlarmMissionPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshActiveAlarmMission()
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        incomingIntent = IncomingIntent(intent, isNewIntent = false)
        val appContainer = (application as RingoutApplication).appContainer
        activeAlarmMissionStore = ActiveAlarmMissionStore(applicationContext)
        alarmMissionCoordinator = AlarmMissionCoordinator(applicationContext)
        playStoreUpdateChecker = PlayStoreUpdateChecker(
            activity = this,
            currentVersionCode = BuildConfig.VERSION_CODE,
        )
        refreshActiveAlarmMission()
        playStoreUpdateChecker.check {
            isUpdateDialogVisible = true
        }

        setContent {
            val roomActivityPushViewModel: RoomActivityPushClickViewModel = viewModel {
                RoomActivityPushClickViewModel(createSavedStateHandle())
            }
            val latestIncomingIntent = incomingIntent
            LaunchedEffect(latestIncomingIntent) {
                latestIncomingIntent?.let { incoming ->
                    roomActivityPushViewModel.receive(incoming.intent, incoming.isNewIntent)
                }
            }
            PlatformAppHost(appContainer) { extraContent ->
                App(
                    appContainer = appContainer,
                    appVersion = BuildConfig.VERSION_NAME,
                    myPageExtraContent = extraContent,
                    activeAlarmMission = activeAlarmMission,
                    activeAlarmMissionLocation = activeAlarmMissionLocation,
                    onActiveAlarmMissionExpired = ::handleActiveAlarmMissionExpired,
                    onActiveAlarmMissionForceEnd = { handleActiveAlarmMissionForceEnd(it) },
                    onActiveAlarmMissionForceEndHoldStarted =
                        alarmMissionCoordinator::recordForceEndHoldStarted,
                    onActiveAlarmMissionForceEndHoldCancelled =
                        alarmMissionCoordinator::recordForceEndHoldCancelled,
                    onActiveAlarmMissionForceEndHoldCompleted =
                        alarmMissionCoordinator::recordForceEndHoldCompleted,
                    pendingRoomActivityPush = roomActivityPushViewModel.pendingRequest,
                    onRoomActivityPushHandled = roomActivityPushViewModel::acknowledge,
                )
            }
            if (isUpdateDialogVisible) {
                RingoutTheme(themeMode = ThemeMode.Light) {
                    AppUpdateDialog(
                        onDismissRequest = { isUpdateDialogVisible = false },
                        onUpdateClick = {
                            isUpdateDialogVisible = false
                            playStoreUpdateChecker.openStore()
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntent = IncomingIntent(intent, isNewIntent = true)
    }

    override fun onStart() {
        super.onStart()
        activeAlarmMissionStore.registerListener(activeAlarmMissionPreferenceListener)
    }

    override fun onResume() {
        super.onResume()
        refreshActiveAlarmMission()
        alarmMissionCoordinator.resumeTracking()
    }

    override fun onStop() {
        activeAlarmMissionStore.unregisterListener(activeAlarmMissionPreferenceListener)
        super.onStop()
    }

    private fun handleActiveAlarmMissionExpired() {
        alarmMissionCoordinator.handleDeadline(activeAlarmMission?.occurrenceId)
    }

    private fun handleActiveAlarmMissionForceEnd(occurrenceId: String): ForceEndMissionResult {
        val result = alarmMissionCoordinator.forceEnd(occurrenceId)
        refreshActiveAlarmMission()
        return result
    }

    private fun refreshActiveAlarmMission() {
        val mission = activeAlarmMissionStore.read()
        activeAlarmMission = mission
        activeAlarmMissionLocation = mission?.let { activeMission ->
            activeAlarmMissionStore.readLastLocation(activeMission.occurrenceId)
        }
    }
}
