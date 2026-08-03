package com.joon.ringout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.rememberAlarmController
import com.joon.ringout.presentation.activemission.ActiveAlarmTrackingScreen
import com.joon.ringout.presentation.alarmsound.AlarmSoundScreen
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import com.joon.ringout.presentation.alarmsetup.AlarmSetupScreen
import com.joon.ringout.presentation.alarmsetup.components.weekdaySummary
import com.joon.ringout.presentation.destination.DefaultDestinationSelection
import com.joon.ringout.presentation.destination.DestinationMapScreen
import com.joon.ringout.presentation.destination.DestinationSelection
import com.joon.ringout.presentation.home.HomeAlarm
import com.joon.ringout.presentation.home.HomeScreen
import com.joon.ringout.presentation.settings.SettingsScreen
import kotlin.random.Random

@Composable
fun App(
    appVersion: String = "",
    activeAlarmMission: ActiveAlarmMission? = null,
    activeAlarmMissionLocation: ActiveAlarmMissionLocation? = null,
    onActiveAlarmMissionExpired: () -> Unit = {},
) {
    val themeController = rememberThemeController()

    SystemBarAppearanceEffect(themeController.themeMode)

    RingoutTheme(themeMode = themeController.themeMode) {
        RingoutAppContent(
            themeMode = themeController.themeMode,
            appVersion = appVersion,
            activeAlarmMission = activeAlarmMission,
            activeAlarmMissionLocation = activeAlarmMissionLocation,
            onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
            onThemeModeChange = themeController::setThemeMode,
        )
    }
}

@Composable
private fun RingoutAppContent(
    themeMode: ThemeMode,
    appVersion: String,
    activeAlarmMission: ActiveAlarmMission?,
    activeAlarmMissionLocation: ActiveAlarmMissionLocation?,
    onActiveAlarmMissionExpired: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    var destinationName by rememberSaveable { mutableStateOf(DefaultDestinationSelection.name) }
    var destinationAddress by rememberSaveable { mutableStateOf(DefaultDestinationSelection.address) }
    var destinationLatitude by rememberSaveable { mutableStateOf(DefaultDestinationSelection.latitude) }
    var destinationLongitude by rememberSaveable { mutableStateOf(DefaultDestinationSelection.longitude) }
    var alarmSoundName by rememberSaveable { mutableStateOf(DefaultAlarmSoundName) }
    var alarmSoundUri by rememberSaveable { mutableStateOf<String?>(null) }
    var screenName by rememberSaveable { mutableStateOf(AppScreen.Home.name) }
    var handledActiveAlarmOccurrenceId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var editingAlarmId by rememberSaveable { mutableStateOf<String?>(null) }
    var alarms by remember { mutableStateOf<List<HomeAlarm>?>(null) }
    var alarmScheduleError by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = DestinationSelection(
        name = destinationName,
        address = destinationAddress,
        latitude = destinationLatitude,
        longitude = destinationLongitude,
    )
    val alarmSound = AlarmSoundSelection(
        name = alarmSoundName,
        uri = alarmSoundUri,
    )
    val requestedScreen = AppScreen.valueOf(screenName)
    val screen = if (
        requestedScreen == AppScreen.ActiveAlarmTracking &&
        activeAlarmMission == null
    ) {
        AppScreen.Home
    } else {
        requestedScreen
    }
    val alarmController = rememberAlarmController(
        onScheduled = { request ->
            val wasEnabled = alarms.orEmpty()
                .firstOrNull { it.id == request.id }
                ?.isEnabled
                ?: true
            alarms = alarms.orEmpty().replaceOrAppend(
                request.toHomeAlarm(enabled = wasEnabled),
            )
            editingAlarmId = null
            screenName = AppScreen.Home.name
        },
        onError = { alarmScheduleError = it },
    )
    val savedAlarms = remember(alarmController) {
        alarmController.savedAlarms.map { saved ->
            saved.request.toHomeAlarm(enabled = saved.enabled)
        }
    }
    val visibleAlarms = alarms ?: savedAlarms
    val editingAlarm = editingAlarmId?.let { alarmId ->
        visibleAlarms.firstOrNull { it.id == alarmId }
    }
    val alarmSetupScreen = if (editingAlarmId == null) {
        AppScreen.AddAlarm
    } else {
        AppScreen.EditAlarm
    }
    val initialSelectedDays = when {
        editingAlarm == null -> DefaultSelectedDays
        editingAlarm.repeatEnabled -> editingAlarm.selectedDays
        else -> emptyList()
    }

    LaunchedEffect(alarmController) {
        if (alarms == null) alarms = savedAlarms
        alarmController.ensureFullScreenAccess()
    }

    LaunchedEffect(activeAlarmMission?.occurrenceId) {
        val occurrenceId = activeAlarmMission?.occurrenceId
        when {
            occurrenceId == null -> {
                handledActiveAlarmOccurrenceId = null
                if (screenName == AppScreen.ActiveAlarmTracking.name) {
                    screenName = AppScreen.Home.name
                }
            }

            handledActiveAlarmOccurrenceId != occurrenceId -> {
                handledActiveAlarmOccurrenceId = occurrenceId
                editingAlarmId = null
                screenName = AppScreen.Home.name
            }
        }
    }

    if (alarmScheduleError != null) {
        AlertDialog(
            onDismissRequest = { alarmScheduleError = null },
            title = { Text("알람을 처리할 수 없습니다") },
            text = { Text(alarmScheduleError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { alarmScheduleError = null }) {
                    Text("확인")
                }
            },
        )
    }

    when (screen) {
        AppScreen.Home -> HomeScreen(
            alarms = visibleAlarms,
            activeAlarmMission = activeAlarmMission,
            onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
            onAddAlarm = {
                editingAlarmId = null
                destinationName = DefaultDestinationSelection.name
                destinationAddress = DefaultDestinationSelection.address
                destinationLatitude = DefaultDestinationSelection.latitude
                destinationLongitude = DefaultDestinationSelection.longitude
                alarmSoundName = DefaultAlarmSoundName
                alarmSoundUri = null
                screenName = AppScreen.AddAlarm.name
            },
            onAlarmClick = { alarmId ->
                visibleAlarms.firstOrNull { it.id == alarmId }?.let { alarm ->
                    editingAlarmId = alarm.id
                    destinationName = alarm.destination.ifBlank {
                        DefaultDestinationSelection.name
                    }
                    destinationAddress = alarm.targetAddress.ifBlank {
                        DefaultDestinationSelection.address
                    }
                    destinationLatitude =
                        alarm.targetLatitude ?: DefaultDestinationSelection.latitude
                    destinationLongitude =
                        alarm.targetLongitude ?: DefaultDestinationSelection.longitude
                    alarmSoundName = alarm.alarmSoundName.ifBlank {
                        DefaultAlarmSoundName
                    }
                    alarmSoundUri = alarm.alarmSoundUri
                    screenName = AppScreen.EditAlarm.name
                }
            },
            onAlarmEnabledChange = { alarmId, enabled ->
                alarmController.setEnabled(alarmId, enabled)
                alarms = visibleAlarms.map { alarm ->
                    if (alarm.id == alarmId) alarm.copy(isEnabled = enabled) else alarm
                }
            },
            onAlarmDelete = { alarmId ->
                if (alarmController.deleteAlarm(alarmId)) {
                    alarms = visibleAlarms.filterNot { alarm -> alarm.id == alarmId }
                }
            },
            onActiveAlarmMissionClick = {
                screenName = AppScreen.ActiveAlarmTracking.name
            },
            onSettingsClick = { screenName = AppScreen.Settings.name },
        )

        AppScreen.ActiveAlarmTracking -> activeAlarmMission?.let { mission ->
            ActiveAlarmTrackingScreen(
                mission = mission,
                currentLocation = activeAlarmMissionLocation,
                onBackClick = { screenName = AppScreen.Home.name },
                onExpired = onActiveAlarmMissionExpired,
            )
        }

        AppScreen.Settings -> SettingsScreen(
            themeMode = themeMode,
            appVersion = appVersion,
            onThemeModeChange = onThemeModeChange,
            onBackClick = { screenName = AppScreen.Home.name },
        )

        AppScreen.AddAlarm,
        AppScreen.EditAlarm,
        AppScreen.Destination,
        AppScreen.AlarmSound,
        -> Box(Modifier.fillMaxSize()) {
            AlarmSetupScreen(
                destination = destination.name,
                alarmSound = alarmSound,
                initialTime = editingAlarm?.time ?: DefaultAlarmTime,
                initialSelectedDays = initialSelectedDays,
                initialLimitMinutes = editingAlarm?.timeLimitMinutes ?: DefaultLimitMinutes,
                onBackClick = {
                    editingAlarmId = null
                    screenName = AppScreen.Home.name
                },
                onDestinationClick = { screenName = AppScreen.Destination.name },
                onAlarmSoundClick = { screenName = AppScreen.AlarmSound.name },
                onSaveClick = { time, selectedDays, repeatEnabled, limitMinutes, alarmSound ->
                    alarmController.schedule(
                        AlarmScheduleRequest(
                            id = editingAlarmId
                                ?: "alarm-${Random.nextInt(1, Int.MAX_VALUE)}",
                            time = time,
                            selectedDays = selectedDays,
                            repeatEnabled = repeatEnabled,
                            limitMinutes = limitMinutes,
                            destinationName = destination.name,
                            destinationAddress = destination.address,
                            destinationLatitude = destination.latitude,
                            destinationLongitude = destination.longitude,
                            alarmSoundName = alarmSound.name,
                            alarmSoundUri = alarmSound.uri,
                        ),
                    )
                },
            )

            if (screen == AppScreen.Destination) {
                DestinationMapScreen(
                    initialSelection = destination,
                    onBackClick = { screenName = alarmSetupScreen.name },
                    onConfirmClick = { selectedDestination ->
                        destinationName = selectedDestination.name
                        destinationAddress = selectedDestination.address
                        destinationLatitude = selectedDestination.latitude
                        destinationLongitude = selectedDestination.longitude
                        screenName = alarmSetupScreen.name
                    },
                )
            }

            if (screen == AppScreen.AlarmSound) {
                AlarmSoundScreen(
                    selectedSound = alarmSound,
                    onBackClick = { screenName = alarmSetupScreen.name },
                    onSaveClick = { selectedSound ->
                        alarmSoundName = selectedSound.name
                        alarmSoundUri = selectedSound.uri
                        screenName = alarmSetupScreen.name
                    },
                )
            }
        }
    }
}

private enum class AppScreen {
    Home,
    AddAlarm,
    EditAlarm,
    Destination,
    AlarmSound,
    Settings,
    ActiveAlarmTracking,
}

private const val DefaultAlarmTime = "06:20"
private val DefaultSelectedDays = listOf("월", "화", "수", "금")
private const val DefaultLimitMinutes = 13
private const val DefaultAlarmSoundName = "Ring Ring Ring"

private fun AlarmScheduleRequest.toHomeAlarm(enabled: Boolean): HomeAlarm = HomeAlarm(
    id = id,
    time = time,
    days = if (repeatEnabled) weekdaySummary(selectedDays) else "한 번",
    destination = destinationName,
    timeLimitMinutes = limitMinutes,
    isEnabled = enabled,
    targetAddress = destinationAddress,
    targetLatitude = destinationLatitude,
    targetLongitude = destinationLongitude,
    alarmSoundName = alarmSoundName,
    alarmSoundUri = alarmSoundUri,
    selectedDays = selectedDays,
    repeatEnabled = repeatEnabled,
)

private fun List<HomeAlarm>.replaceOrAppend(updatedAlarm: HomeAlarm): List<HomeAlarm> =
    if (any { it.id == updatedAlarm.id }) {
        map { alarm ->
            if (alarm.id == updatedAlarm.id) updatedAlarm else alarm
        }
    } else {
        this + updatedAlarm
    }
