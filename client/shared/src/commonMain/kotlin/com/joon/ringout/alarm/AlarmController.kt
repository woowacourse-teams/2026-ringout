package com.joon.ringout.alarm

import androidx.compose.runtime.Composable

data class AlarmScheduleRequest(
    val id: String,
    val time: String,
    val selectedDays: List<String>,
    val repeatEnabled: Boolean,
    val limitMinutes: Int,
    val destinationName: String,
    val destinationAddress: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val targetDistanceKm: Double = 1.2,
    val alarmSoundName: String,
    val alarmSoundUri: String?,
)

class AlarmController(
    val schedule: (AlarmScheduleRequest) -> Unit,
    val setEnabled: (alarmId: String, enabled: Boolean) -> Unit,
    val deleteAlarm: (alarmId: String) -> Boolean,
    val savedAlarms: List<SavedAlarmSchedule> = emptyList(),
    val ensureFullScreenAccess: () -> Unit = {},
)

data class SavedAlarmSchedule(
    val request: AlarmScheduleRequest,
    val enabled: Boolean,
)

@Composable
expect fun rememberAlarmController(
    onScheduled: (AlarmScheduleRequest) -> Unit,
    onError: (String) -> Unit,
): AlarmController
