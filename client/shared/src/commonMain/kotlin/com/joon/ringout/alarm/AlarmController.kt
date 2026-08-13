package com.joon.ringout.alarm

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
    val deleteAlarm: (alarmId: String) -> Unit,
    val savedAlarms: Flow<List<SavedAlarmSchedule>> = flowOf(emptyList()),
    val ensureFullScreenAccess: () -> Unit = {},
)

data class SavedAlarmSchedule(
    val request: AlarmScheduleRequest,
    val enabled: Boolean,
)

/**
 * Creates the platform alarm controller.
 *
 * [onSaveCompleted] is invoked only after the platform's required preparation and the data
 * mutation both succeed. On both platforms this means the system alarm side effect and Room
 * persistence have completed successfully.
 */
@Composable
expect fun rememberAlarmController(
    onSaveCompleted: (AlarmScheduleRequest) -> Unit,
    onSaveError: (AlarmScheduleRequest, String) -> Unit,
    onError: (String) -> Unit,
): AlarmController
