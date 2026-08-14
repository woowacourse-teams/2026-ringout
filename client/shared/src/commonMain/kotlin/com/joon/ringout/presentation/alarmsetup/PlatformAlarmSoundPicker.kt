package com.joon.ringout.presentation.alarmsetup

import androidx.compose.runtime.Composable

data class AlarmSoundSelection(
    val name: String,
    val uri: String?,
)

internal expect val PlatformDefaultAlarmSoundName: String

class DeviceAlarmSoundController internal constructor(
    val sounds: List<AlarmSoundSelection>,
    private val previewSound: (AlarmSoundSelection) -> Unit,
    private val stopSoundPreview: () -> Unit,
) {
    fun preview(selection: AlarmSoundSelection) {
        previewSound(selection)
    }

    fun stopPreview() {
        stopSoundPreview()
    }
}

@Composable
expect fun rememberDeviceAlarmSoundController(): DeviceAlarmSoundController
