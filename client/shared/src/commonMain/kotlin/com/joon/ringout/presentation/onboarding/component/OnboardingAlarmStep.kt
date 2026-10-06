package com.joon.ringout.presentation.onboarding.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.alarmsound.components.AlarmSoundListItem
import com.joon.ringout.presentation.alarmsetup.AlarmSetupUiState
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import com.joon.ringout.presentation.alarmsetup.components.DestinationCard
import com.joon.ringout.presentation.alarmsetup.components.LimitTimeCard
import com.joon.ringout.presentation.alarmsetup.components.TimePickerCard
import com.joon.ringout.presentation.alarmsetup.components.WeekdaySelector
import com.joon.ringout.presentation.alarmsetup.toAlarmTimePickerValue
import com.joon.ringout.presentation.onboarding.OnboardingStep

@Composable
internal fun OnboardingAlarmStep(
    step: OnboardingStep,
    alarm: AlarmSetupUiState,
    sounds: List<AlarmSoundSelection>,
    onAmPmChange: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onDayClick: (String) -> Unit,
    onDestinationClick: () -> Unit,
    onLimitMinutesChange: (Int) -> Unit,
    onSoundClick: (AlarmSoundSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (step) {
            OnboardingStep.Time -> {
                val time = alarm.time.toAlarmTimePickerValue()
                TimePickerCard(
                    isAm = time.isAm,
                    hour = time.hour,
                    minute = time.minute,
                    onAmPmChange = onAmPmChange,
                    onHourChange = onHourChange,
                    onMinuteChange = onMinuteChange,
                )
            }
            OnboardingStep.Weekdays -> WeekdaySelector(alarm.selectedDays, onDayClick)
            OnboardingStep.Destination -> DestinationCard(alarm.destination?.name.orEmpty(), onDestinationClick)
            OnboardingStep.Interval -> LimitTimeCard(alarm.limitMinutes, onLimitMinutesChange)
            OnboardingStep.Sound -> sounds.forEach { sound ->
                AlarmSoundListItem(
                    sound = sound,
                    selected = sound.uri == alarm.alarmSound.uri,
                    onClick = { onSoundClick(sound) },
                )
            }
        }
    }
}

@Preview(widthDp = 354)
@Composable
private fun OnboardingAlarmStepPreview() {
    RingoutTheme {
        OnboardingAlarmStep(
            step = OnboardingStep.Time,
            alarm = AlarmSetupUiState(),
            sounds = emptyList(),
            onAmPmChange = {},
            onHourChange = {},
            onMinuteChange = {},
            onDayClick = {},
            onDestinationClick = {},
            onLimitMinutesChange = {},
            onSoundClick = {},
        )
    }
}
