package com.joon.ringout

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.ActiveAlarmMissionStore
import com.joon.ringout.alarm.AlarmMissionCoordinator

class MainActivity : ComponentActivity() {
    private lateinit var activeAlarmMissionStore: ActiveAlarmMissionStore
    private lateinit var alarmMissionCoordinator: AlarmMissionCoordinator
    private var activeAlarmMission by mutableStateOf<ActiveAlarmMission?>(null)
    private var activeAlarmMissionLocation by mutableStateOf<ActiveAlarmMissionLocation?>(null)
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
        activeAlarmMissionStore = ActiveAlarmMissionStore(applicationContext)
        alarmMissionCoordinator = AlarmMissionCoordinator(applicationContext)
        refreshActiveAlarmMission()

        setContent {
            App(
                appVersion = BuildConfig.VERSION_NAME,
                activeAlarmMission = activeAlarmMission,
                activeAlarmMissionLocation = activeAlarmMissionLocation,
                onActiveAlarmMissionExpired = ::handleActiveAlarmMissionExpired,
            )
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

    private fun refreshActiveAlarmMission() {
        val mission = activeAlarmMissionStore.read()
        activeAlarmMission = mission
        activeAlarmMissionLocation = mission?.let { activeMission ->
            activeAlarmMissionStore.readLastLocation(activeMission.occurrenceId)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(appVersion = BuildConfig.VERSION_NAME)
}
