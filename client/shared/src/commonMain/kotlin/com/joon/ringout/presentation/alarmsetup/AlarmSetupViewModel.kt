package com.joon.ringout.presentation.alarmsetup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.newAlarmId
import com.joon.ringout.presentation.destination.DestinationSelection

class AlarmSetupViewModel(
    private val createAlarmId: () -> String = ::newAlarmId,
) : ViewModel() {
    var uiState by mutableStateOf(AlarmSetupUiState())
        private set

    fun startCreating(initialTime: String) {
        uiState = AlarmSetupUiState(time = initialTime)
    }

    fun startEditing(request: AlarmScheduleRequest) {
        uiState = AlarmSetupUiState(
            alarmId = request.id,
            time = request.time,
            selectedDays = if (request.repeatEnabled) request.selectedDays else emptyList(),
            limitMinutes = request.limitMinutes.coerceIn(
                minimumValue = MinAlarmLimitMinutes,
                maximumValue = MaxAlarmLimitMinutes,
            ),
            destination = DestinationSelection(
                name = request.destinationName,
                address = request.destinationAddress,
                latitude = request.destinationLatitude,
                longitude = request.destinationLongitude,
            ),
            alarmSound = AlarmSoundSelection(
                name = request.alarmSoundName,
                uri = request.alarmSoundUri,
            ),
        )
    }

    fun updateAmPm(isAm: Boolean) {
        updateTime { copy(isAm = isAm) }
    }

    fun updateHour(hour: Int) {
        updateTime { copy(hour = hour) }
    }

    fun updateMinute(minute: Int) {
        updateTime { copy(minute = minute) }
    }

    fun toggleDay(day: String) {
        if (day !in AlarmSetupWeekdays) return

        val selectedDays = uiState.selectedDays.toSet().let { currentDays ->
            if (day in currentDays) currentDays - day else currentDays + day
        }
        uiState = uiState.copy(
            selectedDays = AlarmSetupWeekdays.filter(selectedDays::contains),
        )
    }

    fun updateLimitMinutes(minutes: Int) {
        uiState = uiState.copy(
            limitMinutes = minutes.coerceIn(
                minimumValue = MinAlarmLimitMinutes,
                maximumValue = MaxAlarmLimitMinutes,
            ),
        )
    }

    fun updateDestination(destination: DestinationSelection) {
        uiState = uiState.copy(destination = destination)
    }

    fun updateAlarmSound(alarmSound: AlarmSoundSelection) {
        uiState = uiState.copy(alarmSound = alarmSound)
    }

    fun createScheduleRequest(): AlarmScheduleRequest? {
        val draft = uiState
        val destination = draft.destination?.takeIf { draft.canSave } ?: return null

        return AlarmScheduleRequest(
            id = draft.alarmId ?: createAlarmId(),
            time = draft.time,
            selectedDays = draft.selectedDays,
            repeatEnabled = draft.repeatEnabled,
            limitMinutes = draft.limitMinutes,
            destinationName = destination.name,
            destinationAddress = destination.address,
            destinationLatitude = destination.latitude,
            destinationLongitude = destination.longitude,
            alarmSoundName = draft.alarmSound.name,
            alarmSoundUri = draft.alarmSound.uri,
        )
    }

    private inline fun updateTime(
        transform: AlarmTimePickerValue.() -> AlarmTimePickerValue,
    ) {
        uiState = uiState.copy(
            time = uiState.time
                .toAlarmTimePickerValue()
                .transform()
                .to24HourString(),
        )
    }
}
