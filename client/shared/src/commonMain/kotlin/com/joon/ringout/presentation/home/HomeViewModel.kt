package com.joon.ringout.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.presentation.common.weekdaySummary
import com.joon.ringout.presentation.home.model.HomeAlarm
import com.joon.ringout.presentation.home.model.HomeUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class HomeViewModel : ViewModel() {
    internal sealed interface Command {
        data class SetAlarmEnabled(
            val alarmId: String,
            val enabled: Boolean,
        ) : Command

        data class DeleteAlarm(
            val alarmId: String,
        ) : Command
    }

    var uiState by mutableStateOf(HomeUiState())
        private set

    internal suspend fun observeAlarms(savedAlarms: Flow<List<SavedAlarmSchedule>>) {
        savedAlarms
            .catch { error ->
                if (error is CancellationException) throw error
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: HomeAlarmLoadErrorMessage,
                )
            }
            .collect { alarms ->
                uiState = uiState.copy(
                    isLoading = false,
                    alarms = alarms.map(SavedAlarmSchedule::toHomeAlarm),
                    errorMessage = null,
                )
            }
    }

    internal fun onAlarmEnabledChange(
        alarmId: String,
        enabled: Boolean,
    ): Command = Command.SetAlarmEnabled(
        alarmId = alarmId,
        enabled = enabled,
    )

    internal fun onAlarmDelete(alarmId: String): Command =
        Command.DeleteAlarm(alarmId = alarmId)

    fun alarmScheduleRequest(alarmId: String): AlarmScheduleRequest? =
        uiState.alarms
            .firstOrNull { alarm -> alarm.id == alarmId }
            ?.toAlarmScheduleRequestOrNull()

    fun showError(message: String) {
        uiState = uiState.copy(errorMessage = message)
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }
}

private fun SavedAlarmSchedule.toHomeAlarm(): HomeAlarm = HomeAlarm(
    id = request.id,
    time = request.time,
    days = if (request.repeatEnabled) weekdaySummary(request.selectedDays) else "한 번",
    destination = request.destinationName,
    timeLimitMinutes = request.limitMinutes,
    isEnabled = enabled,
    targetAddress = request.destinationAddress,
    targetLatitude = request.destinationLatitude,
    targetLongitude = request.destinationLongitude,
    targetDistanceKm = request.targetDistanceKm,
    alarmSoundName = request.alarmSoundName,
    alarmSoundUri = request.alarmSoundUri,
    selectedDays = request.selectedDays,
    repeatEnabled = request.repeatEnabled,
)

private fun HomeAlarm.toAlarmScheduleRequestOrNull(): AlarmScheduleRequest? {
    val destinationLatitude = targetLatitude ?: return null
    val destinationLongitude = targetLongitude ?: return null

    return AlarmScheduleRequest(
        id = id,
        time = time,
        selectedDays = selectedDays,
        repeatEnabled = repeatEnabled,
        limitMinutes = timeLimitMinutes,
        destinationName = destination,
        destinationAddress = targetAddress,
        destinationLatitude = destinationLatitude,
        destinationLongitude = destinationLongitude,
        targetDistanceKm = targetDistanceKm,
        alarmSoundName = alarmSoundName,
        alarmSoundUri = alarmSoundUri,
    )
}

internal const val HomeAlarmLoadErrorMessage = "저장된 알람을 불러오지 못했습니다."
