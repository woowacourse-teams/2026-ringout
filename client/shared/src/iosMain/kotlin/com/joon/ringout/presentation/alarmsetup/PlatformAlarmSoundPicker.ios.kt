package com.joon.ringout.presentation.alarmsetup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal actual val PlatformDefaultAlarmSoundName = "기본 알람음"

@Composable
actual fun rememberDeviceAlarmSoundController(): DeviceAlarmSoundController =
    remember {
        DeviceAlarmSoundController(
            sounds = listOf(
                AlarmSoundSelection(
                    name = PlatformDefaultAlarmSoundName,
                    uri = null,
                ),
            ),
            previewSound = {},
            stopSoundPreview = {},
        )
    }
