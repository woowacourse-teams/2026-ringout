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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.alarmguide.AlarmCreationGuideStep
import com.joon.ringout.presentation.alarmguide.component.alarmCreationGuideTarget
import com.joon.ringout.presentation.alarmsetup.components.AlarmSoundCard
import com.joon.ringout.presentation.alarmsetup.components.DestinationCard
import com.joon.ringout.presentation.alarmsetup.components.LimitTimeCard
import com.joon.ringout.presentation.alarmsetup.components.SaveAlarmButton
import com.joon.ringout.presentation.alarmsetup.components.SetupBackButton
import com.joon.ringout.presentation.alarmsetup.components.TimePickerCard
import com.joon.ringout.presentation.alarmsetup.components.WeekdaySelector
import com.joon.ringout.presentation.alarmsetup.components.alarmSetupColors
import com.joon.ringout.presentation.destination.DestinationSelection

@Composable
fun AlarmSetupScreen(
    uiState: AlarmSetupUiState,
    onAmPmChange: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onDayClick: (String) -> Unit,
    onLimitMinutesChange: (Int) -> Unit,
    onBackClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onAlarmSoundClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alarmTime = uiState.time.toAlarmTimePickerValue()
    val colors = alarmSetupColors()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SetupBackButton(
                enabled = !uiState.isSaveInProgress,
                onClick = onBackClick,
            )
            TimePickerCard(
                isAm = alarmTime.isAm,
                hour = alarmTime.hour,
                minute = alarmTime.minute,
                onAmPmChange = onAmPmChange,
                onHourChange = onHourChange,
                onMinuteChange = onMinuteChange,
                modifier = Modifier.alarmCreationGuideTarget(AlarmCreationGuideStep.Time),
            )
            WeekdaySelector(
                selectedDays = uiState.selectedDays,
                onDayClick = onDayClick,
                modifier = Modifier.alarmCreationGuideTarget(AlarmCreationGuideStep.Weekdays),
            )
            DestinationCard(
                destination = uiState.destinationDisplayName,
                onClick = onDestinationClick,
                modifier = Modifier.alarmCreationGuideTarget(
                    step = AlarmCreationGuideStep.Destination,
                    onClick = onDestinationClick,
                ),
            )
            LimitTimeCard(
                minutes = uiState.limitMinutes,
                onMinutesChange = onLimitMinutesChange,
                modifier = Modifier.alarmCreationGuideTarget(AlarmCreationGuideStep.Interval),
            )
            AlarmSoundCard(
                soundName = uiState.alarmSound.name,
                onClick = onAlarmSoundClick,
                modifier = Modifier.alarmCreationGuideTarget(
                    step = AlarmCreationGuideStep.Sound,
                    onClick = onAlarmSoundClick,
                ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(start = 20.dp, end = 20.dp, bottom = 47.dp, top = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            SaveAlarmButton(
                enabled = uiState.canSave && !uiState.isSaveInProgress,
                isInProgress = uiState.isSaveInProgress,
                onClick = onSaveClick,
                modifier = Modifier.alarmCreationGuideTarget(
                    step = AlarmCreationGuideStep.CreateAlarm,
                    onClick = {
                        if (uiState.canSave && !uiState.isSaveInProgress) {
                            onSaveClick()
                        }
                    },
                ),
            )
        }
    }
}

@Preview(widthDp = 402, heightDp = 941)
@Preview(name = "큰 글자 · 작은 화면", widthDp = 320, heightDp = 568, fontScale = 2f)
@Composable
private fun AlarmSetupScreenDarkPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmSetupScreen(
            uiState = AlarmSetupUiState(
                alarmSound = AlarmSoundSelection("Ring Ring Ring", null),
            ),
            onAmPmChange = {},
            onHourChange = {},
            onMinuteChange = {},
            onDayClick = {},
            onLimitMinutesChange = {},
            onBackClick = {},
            onDestinationClick = {},
            onAlarmSoundClick = {},
            onSaveClick = {},
        )
    }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun AlarmSetupScreenLightPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        AlarmSetupScreen(
            uiState = AlarmSetupUiState(
                destination = DestinationSelection(
                    name = "집",
                    address = "서울특별시 중구 세종대로 110",
                    latitude = 37.5665851,
                    longitude = 126.9782038,
                ),
                alarmSound = AlarmSoundSelection("Ring Ring Ring", null),
            ),
            onAmPmChange = {},
            onHourChange = {},
            onMinuteChange = {},
            onDayClick = {},
            onLimitMinutesChange = {},
            onBackClick = {},
            onDestinationClick = {},
            onAlarmSoundClick = {},
            onSaveClick = {},
        )
    }
}
