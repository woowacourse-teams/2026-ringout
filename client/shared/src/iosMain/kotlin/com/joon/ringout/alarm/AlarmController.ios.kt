package com.joon.ringout.alarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberAlarmController(
    onScheduled: (AlarmScheduleRequest) -> Unit,
    onError: (String) -> Unit,
): AlarmController {
    val currentOnError = rememberUpdatedState(onError)
    return remember {
        AlarmController(
            schedule = { currentOnError.value("알람 예약은 현재 Android에서 지원됩니다.") },
            setEnabled = { _, _ -> },
            deleteAlarm = { true },
        )
    }
}
