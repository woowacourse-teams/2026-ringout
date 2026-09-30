package com.joon.ringout.presentation.roomcreate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.joon.ringout.presentation.alarmsetup.AlarmTimePickerValue
import com.joon.ringout.presentation.alarmsetup.to24HourString
import com.joon.ringout.presentation.alarmsetup.toAlarmTimePickerValue
import com.joon.ringout.presentation.common.WeekdayOrder
import com.joon.ringout.presentation.roomcreate.model.RoomCreateDraft
import com.joon.ringout.presentation.roomcreate.model.RoomCreateUiState

internal class RoomCreateViewModel : ViewModel() {
    var uiState by mutableStateOf(RoomCreateUiState())
        private set

    fun initializeTimeIfNeeded(initialTime24Hour: String) {
        if (uiState.time24Hour == null) {
            uiState = uiState.copy(time24Hour = initialTime24Hour)
        }
    }

    fun updateName(name: String) {
        uiState = uiState.copy(name = name)
    }

    fun updateIntroduction(introduction: String) {
        uiState = uiState.copy(introduction = introduction)
    }

    fun toggleDay(day: String) {
        if (day !in WeekdayOrder) return

        val selectedDays = uiState.selectedDays.toSet().let { current ->
            if (day in current) current - day else current + day
        }
        uiState = uiState.copy(
            selectedDays = WeekdayOrder.filter(selectedDays::contains),
        )
    }

    fun updateAmPm(isAm: Boolean) = updateTime { copy(isAm = isAm) }

    fun updateHour(hour: Int) = updateTime { copy(hour = hour) }

    fun updateMinute(minute: Int) = updateTime { copy(minute = minute) }

    fun goToNextStep(): Boolean {
        if (uiState.step >= 3 || !uiState.canContinue) return false
        uiState = uiState.copy(step = uiState.step + 1)
        return true
    }

    fun onBack(onExit: () -> Unit) {
        onExit()
    }

    fun createDraft(): RoomCreateDraft? {
        val state = uiState
        if (state.step != 3 || !state.canContinue) return null

        return RoomCreateDraft(
            name = state.nameValidation.normalizedValue,
            introduction = state.introduction,
            selectedDays = WeekdayOrder.filter(state.selectedDays.toSet()::contains),
            time24Hour = checkNotNull(state.time24Hour),
        )
    }

    private fun updateTime(transform: AlarmTimePickerValue.() -> AlarmTimePickerValue) {
        val pickerValue = (uiState.time24Hour ?: DefaultTime24Hour).toAlarmTimePickerValue()
        uiState = uiState.copy(time24Hour = transform(pickerValue).to24HourString())
    }
}

private const val DefaultTime24Hour = "00:00"
