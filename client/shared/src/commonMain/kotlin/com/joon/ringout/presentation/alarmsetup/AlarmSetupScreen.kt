package com.joon.ringout.presentation.alarmsetup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.alarmsetup.components.AlarmSoundCard
import com.joon.ringout.presentation.alarmsetup.components.DestinationCard
import com.joon.ringout.presentation.alarmsetup.components.LimitTimeCard
import com.joon.ringout.presentation.alarmsetup.components.SaveAlarmButton
import com.joon.ringout.presentation.alarmsetup.components.SetupBackButton
import com.joon.ringout.presentation.alarmsetup.components.TimePickerCard
import com.joon.ringout.presentation.alarmsetup.components.TimeSettingDialog
import com.joon.ringout.presentation.alarmsetup.components.WeekdaySelector
import com.joon.ringout.presentation.alarmsetup.components.alarmSetupColors
import com.joon.ringout.presentation.destination.PlatformBackHandler

@Composable
fun AlarmSetupScreen(
    destination: String,
    alarmSound: AlarmSoundSelection,
    initialTime: String = "06:20",
    initialSelectedDays: List<String> = listOf("월", "화", "수", "금"),
    initialLimitMinutes: Int = 13,
    onBackClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onAlarmSoundClick: () -> Unit,
    onSaveClick: (
        time: String,
        selectedDays: List<String>,
        repeatEnabled: Boolean,
        limitMinutes: Int,
        alarmSound: AlarmSoundSelection,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialSelectedDaysValue = initialSelectedDays.joinToString(",")
    var limitMinutes by rememberSaveable(
        initialTime,
        initialSelectedDaysValue,
        initialLimitMinutes,
    ) {
        mutableStateOf(initialLimitMinutes)
    }
    var selectedDaysValue by rememberSaveable(
        initialTime,
        initialSelectedDaysValue,
        initialLimitMinutes,
    ) {
        mutableStateOf(initialSelectedDaysValue)
    }
    var alarmTime by rememberSaveable(
        initialTime,
        initialSelectedDaysValue,
        initialLimitMinutes,
    ) {
        mutableStateOf(initialTime)
    }
    var showTimeDialog by rememberSaveable(
        initialTime,
        initialSelectedDaysValue,
        initialLimitMinutes,
    ) {
        mutableStateOf(false)
    }
    val selectedDays = selectedDaysValue.split(",").filter(String::isNotBlank)
    val colors = alarmSetupColors()

    PlatformBackHandler(onBack = onBackClick)

    if (showTimeDialog) {
        TimeSettingDialog(
            initialTime = alarmTime,
            onDismissRequest = { showTimeDialog = false },
            onConfirm = { selectedTime ->
                alarmTime = selectedTime
                showTimeDialog = false
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 117.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SetupBackButton(onClick = onBackClick)
            TimePickerCard(
                time = alarmTime,
                onClick = { showTimeDialog = true },
            )
            WeekdaySelector(
                selectedDays = selectedDays,
                onDayClick = { day ->
                    selectedDaysValue = (
                        if (day in selectedDays) {
                            selectedDays - day
                        } else {
                            selectedDays + day
                        }
                    ).joinToString(",")
                },
            )
            DestinationCard(
                destination = destination,
                onClick = onDestinationClick,
            )
            LimitTimeCard(
                minutes = limitMinutes,
                onMinutesChange = { limitMinutes = it },
            )
            AlarmSoundCard(
                soundName = alarmSound.name,
                onClick = onAlarmSoundClick,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 47.dp),
            contentAlignment = Alignment.Center,
        ) {
            SaveAlarmButton(
                onClick = {
                    onSaveClick(
                        alarmTime,
                        selectedDays,
                        selectedDays.isNotEmpty(),
                        limitMinutes,
                        alarmSound,
                    )
                },
            )
        }
    }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun AlarmSetupScreenDarkPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmSetupScreen(
            destination = "집 앞에 수원천",
            alarmSound = AlarmSoundSelection("Ring Ring Ring", null),
            onBackClick = {},
            onDestinationClick = {},
            onAlarmSoundClick = {},
            onSaveClick = { _, _, _, _, _ -> },
        )
    }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun AlarmSetupScreenLightPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        AlarmSetupScreen(
            destination = "집 앞에 수원천",
            alarmSound = AlarmSoundSelection("Ring Ring Ring", null),
            onBackClick = {},
            onDestinationClick = {},
            onAlarmSoundClick = {},
            onSaveClick = { _, _, _, _, _ -> },
        )
    }
}
