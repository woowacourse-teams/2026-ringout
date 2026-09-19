package com.joon.ringout.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.alarmsetup.AlarmSetupUiState
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import com.joon.ringout.presentation.alarmsetup.components.SetupBackButton
import com.joon.ringout.presentation.alarmsetup.components.alarmSetupColors
import com.joon.ringout.presentation.onboarding.component.OnboardingAlarmStep
import com.joon.ringout.presentation.onboarding.component.OnboardingPageIndicator
import com.joon.ringout.presentation.onboarding.component.OnboardingPrimaryButton

@Composable
internal fun OnboardingScreen(
    uiState: OnboardingUiState,
    alarm: AlarmSetupUiState,
    sounds: List<AlarmSoundSelection>,
    onAmPmChange: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onDayClick: (String) -> Unit,
    onDestinationClick: () -> Unit,
    onLimitMinutesChange: (Int) -> Unit,
    onSoundClick: (AlarmSoundSelection) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    completionEnabled: Boolean = true,
    completionFailed: Boolean = false,
) {
    val busy = alarm.isSaveInProgress || !completionEnabled
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(alarmSetupColors().background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                if (uiState.step != OnboardingStep.Time && !uiState.isAlarmSaved) {
                    SetupBackButton(onClick = onBack, enabled = !busy)
                }
                OnboardingPageIndicator(uiState.steps.size, uiState.currentStepIndex)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val contentMinHeight = (maxHeight - 160.dp).coerceAtLeast(240.dp)
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = uiState.step.title,
                        modifier = Modifier.padding(top = 18.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = uiState.step.description,
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = contentMinHeight)
                            .padding(vertical = 36.dp),
                        contentAlignment = if (uiState.step == OnboardingStep.Sound) {
                            Alignment.TopCenter
                        } else {
                            Alignment.Center
                        },
                    ) {
                        OnboardingAlarmStep(
                            step = uiState.step,
                            alarm = alarm,
                            sounds = sounds,
                            onAmPmChange = onAmPmChange,
                            onHourChange = onHourChange,
                            onMinuteChange = onMinuteChange,
                            onDayClick = onDayClick,
                            onDestinationClick = onDestinationClick,
                            onLimitMinutesChange = onLimitMinutesChange,
                            onSoundClick = onSoundClick,
                        )
                    }
                }
            }
            if (completionFailed) {
                Text(
                    "알람은 저장됐어요. 시작하기를 눌러 완료 처리를 다시 시도해주세요.",
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            OnboardingPrimaryButton(
                label = when {
                    busy -> "저장 중…"
                    uiState.isLastStep -> "시작하기"
                    else -> "다음으로"
                },
                onClick = onNext,
                enabled = !busy && (uiState.step != OnboardingStep.Destination || alarm.canSave),
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            )
        }
    }
}

@Preview(name = "Time dark", widthDp = 402, heightDp = 874)
@Preview(name = "Time compact", widthDp = 320, heightDp = 568, fontScale = 1.3f)
@Composable
private fun OnboardingScreenPreview() = OnboardingPreview(OnboardingStep.Time)

@Preview(name = "Weekdays", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingWeekdaysPreview() = OnboardingPreview(OnboardingStep.Weekdays)

@Preview(name = "Destination empty", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingDestinationPreview() = OnboardingPreview(OnboardingStep.Destination)

@Preview(name = "Interval light", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingIntervalPreview() = OnboardingPreview(OnboardingStep.Interval, ThemeMode.Light)

@Preview(name = "Sound light", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingSoundPreview() = OnboardingPreview(OnboardingStep.Sound, ThemeMode.Light)

@Composable
private fun OnboardingPreview(
    step: OnboardingStep,
    theme: ThemeMode = ThemeMode.Dark,
    includesSoundSelection: Boolean = true,
) {
    RingoutTheme(themeMode = theme) {
        OnboardingScreen(
            uiState = OnboardingUiState(step = step, includesSoundSelection = includesSoundSelection),
            alarm = AlarmSetupUiState(limitMinutes = 5),
            sounds = listOf(AlarmSoundSelection("기본 알람음", null), AlarmSoundSelection("Argon", "argon")),
            onAmPmChange = {},
            onHourChange = {},
            onMinuteChange = {},
            onDayClick = {},
            onDestinationClick = {},
            onLimitMinutesChange = {},
            onSoundClick = {},
            onBack = {},
            onNext = {},
        )
    }
}

@Preview(name = "iOS interval final step", widthDp = 402, heightDp = 874)
@Composable
private fun OnboardingIosIntervalPreview() = OnboardingPreview(
    step = OnboardingStep.Interval,
    theme = ThemeMode.Light,
    includesSoundSelection = false,
)
