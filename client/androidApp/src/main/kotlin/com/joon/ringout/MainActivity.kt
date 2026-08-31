package com.joon.ringout

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.ActiveAlarmMissionStore
import com.joon.ringout.alarm.AlarmMissionCoordinator
import com.joon.ringout.presentation.update.AppUpdateDialog
import com.joon.ringout.presentation.update.PlayStoreUpdateChecker

class MainActivity : ComponentActivity() {
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
            App(
                appContainer = appContainer,
                appVersion = BuildConfig.VERSION_NAME,
                activeAlarmMission = activeAlarmMission,
                activeAlarmMissionLocation = activeAlarmMissionLocation,
                onActiveAlarmMissionExpired = ::handleActiveAlarmMissionExpired,
                onActiveAlarmMissionForceEnd = ::handleActiveAlarmMissionForceEnd,
                onActiveAlarmMissionForceEndHoldStarted =
                    alarmMissionCoordinator::recordForceEndHoldStarted,
                onActiveAlarmMissionForceEndHoldCancelled =
                    alarmMissionCoordinator::recordForceEndHoldCancelled,
                onActiveAlarmMissionForceEndHoldCompleted =
                    alarmMissionCoordinator::recordForceEndHoldCompleted,
            )
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

    private fun handleActiveAlarmMissionForceEnd(occurrenceId: String) {
        alarmMissionCoordinator.forceEnd(occurrenceId)
        refreshActiveAlarmMission()
    }

    private fun refreshActiveAlarmMission() {
        val mission = activeAlarmMissionStore.read()
        activeAlarmMission = mission
        activeAlarmMissionLocation = mission?.let { activeMission ->
            activeAlarmMissionStore.readLastLocation(activeMission.occurrenceId)
        }
    }
}
