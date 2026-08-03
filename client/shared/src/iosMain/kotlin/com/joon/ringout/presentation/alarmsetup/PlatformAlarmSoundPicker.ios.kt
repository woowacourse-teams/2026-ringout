package com.joon.ringout.presentation.alarmsetup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberDeviceAlarmSoundController(): DeviceAlarmSoundController =
    remember {
        DeviceAlarmSoundController(
            sounds = listOf(
                AlarmSoundSelection(
                    name = "기본 알람음",
                    uri = null,
                ),
            ),
            previewSound = {},
            stopSoundPreview = {},
        )
    }
