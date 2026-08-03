package com.joon.ringout.presentation.alarmsound

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.alarmsound.components.AlarmSoundHeader
import com.joon.ringout.presentation.alarmsound.components.AlarmSoundListItem
import com.joon.ringout.presentation.alarmsound.components.AlarmSoundSaveButton
import com.joon.ringout.presentation.alarmsound.components.alarmSoundColors
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import com.joon.ringout.presentation.alarmsetup.rememberDeviceAlarmSoundController
import com.joon.ringout.presentation.destination.PlatformBackHandler

@Composable
fun AlarmSoundScreen(
    selectedSound: AlarmSoundSelection,
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

    fun leaveWithoutSaving() {
        soundController.stopPreview()
        onBackClick()
    }

    PlatformBackHandler(onBack = ::leaveWithoutSaving)

    AlarmSoundScreenContent(
        sounds = availableSounds,
        selectedSound = draftSelection,
        onBackClick = ::leaveWithoutSaving,
        onSoundClick = { sound ->
            selectedName = sound.name
            selectedUri = sound.uri
            soundController.preview(sound)
        },
        onSaveClick = {
            soundController.stopPreview()
            onSaveClick(draftSelection)
        },
        modifier = modifier,
    )
}

@Composable
internal fun AlarmSoundScreenContent(
    sounds: List<AlarmSoundSelection>,
    selectedSound: AlarmSoundSelection,
    onBackClick: () -> Unit,
    onSoundClick: (AlarmSoundSelection) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSoundColors()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
    ) {
        AlarmSoundHeader(onBackClick = onBackClick)
        Spacer(Modifier.height(24.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = sounds,
                key = { sound -> sound.uri ?: "default:${sound.name}" },
            ) { sound ->
                AlarmSoundListItem(
                    sound = sound,
                    selected = sound.sameSoundAs(selectedSound),
                    onClick = { onSoundClick(sound) },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            contentAlignment = Alignment.Center,
        ) {
            AlarmSoundSaveButton(
                onClick = onSaveClick,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
}

internal fun resolveInitialAlarmSoundSelection(
    sounds: List<AlarmSoundSelection>,
    current: AlarmSoundSelection,
): AlarmSoundSelection =
    sounds.firstOrNull { it.sameSoundAs(current) }
        ?: sounds.firstOrNull { it.name == current.name }
        ?: sounds.firstOrNull()
        ?: current

private fun AlarmSoundSelection.sameSoundAs(other: AlarmSoundSelection): Boolean =
    if (uri == null || other.uri == null) {
        uri == null && other.uri == null
    } else {
        uri == other.uri
    }

private val PreviewAlarmSounds = listOf(
    AlarmSoundSelection("Ring Ring Ring", null),
    AlarmSoundSelection("새벽 안개", "preview://dawn"),
    AlarmSoundSelection("부드러운 기상", "preview://soft"),
    AlarmSoundSelection("에너지 차지", "preview://energy"),
    AlarmSoundSelection("자연의 소리", "preview://nature"),
    AlarmSoundSelection("클래식 벨", "preview://classic"),
    AlarmSoundSelection("디지털 비프", "preview://digital"),
)

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun AlarmSoundScreenDarkPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmSoundScreenContent(
            sounds = PreviewAlarmSounds,
            selectedSound = PreviewAlarmSounds.first(),
            onBackClick = {},
            onSoundClick = {},
            onSaveClick = {},
        )
    }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun AlarmSoundScreenLightPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        AlarmSoundScreenContent(
            sounds = PreviewAlarmSounds,
            selectedSound = PreviewAlarmSounds.first(),
            onBackClick = {},
            onSoundClick = {},
            onSaveClick = {},
        )
    }
}
