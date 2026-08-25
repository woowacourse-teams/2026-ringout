package com.joon.ringout.presentation.alarmsetup

import androidx.compose.runtime.Immutable
import com.joon.ringout.presentation.destination.DestinationSelection
import com.joon.ringout.presentation.destination.isConfiguredDestination

@Immutable
data class AlarmSetupUiState(
    val alarmId: String? = null,
    val time: String = DefaultAlarmTime,
    val selectedDays: List<String> = AlarmSetupWeekdays,
    val limitMinutes: Int = DefaultAlarmLimitMinutes,
    val destination: DestinationSelection? = null,
    val alarmSound: AlarmSoundSelection = AlarmSoundSelection(
        name = DefaultAlarmSoundName,
        uri = null,
    ),
) {
    val isEditing: Boolean
        get() = alarmId != null

    val repeatEnabled: Boolean
        get() = selectedDays.isNotEmpty()

    val canSave: Boolean
        get() = destination?.isConfiguredDestination() == true
}

internal const val DefaultAlarmTime = "06:20"
internal const val DefaultAlarmLimitMinutes = 13
internal const val DefaultAlarmSoundName = "기본 알람음"
internal const val MinAlarmLimitMinutes = 1
internal const val MaxAlarmLimitMinutes = 30

internal val AlarmSetupWeekdays = listOf("월", "화", "수", "목", "금", "토", "일")
