package com.joon.ringout.presentation.alarmsound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import com.joon.ringout.presentation.alarmsetup.rememberDeviceAlarmSoundController

@Composable
internal fun AlarmSoundRoute(
    selectedSound: AlarmSoundSelection,
    isActive: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: (AlarmSoundSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val soundController = rememberDeviceAlarmSoundController()
    val availableSounds = soundController.sounds.ifEmpty { listOf(selectedSound) }
    val initialSelection = remember(availableSounds, selectedSound) {
        resolveInitialAlarmSoundSelection(availableSounds, selectedSound)
    }
    var selectedName by rememberSaveable(selectedSound.name, selectedSound.uri) {
        mutableStateOf(initialSelection.name)
    }
    var selectedUri by rememberSaveable(selectedSound.name, selectedSound.uri) {
        mutableStateOf(initialSelection.uri)
    }
    val draftSelection = AlarmSoundSelection(
        name = selectedName,
        uri = selectedUri,
    )

    // Also stop when a covering screen leaves this entry composed but inactive.
    DisposableEffect(soundController, isActive) {
        onDispose(soundController::stopPreview)
    }

    AlarmSoundScreen(
        sounds = availableSounds,
        selectedSound = draftSelection,
        onBackClick = {
            if (isActive) {
                soundController.stopPreview()
                onBackClick()
            }
        },
        onSoundClick = { sound ->
            if (isActive) {
                selectedName = sound.name
                selectedUri = sound.uri
                soundController.preview(sound)
            }
        },
        onSaveClick = {
            if (isActive) {
                soundController.stopPreview()
                onSaveClick(draftSelection)
            }
        },
        modifier = modifier,
    )
}
