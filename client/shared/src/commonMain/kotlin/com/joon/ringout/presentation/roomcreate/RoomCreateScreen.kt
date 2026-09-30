package com.joon.ringout.presentation.roomcreate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.alarmsetup.AlarmTimePickerValue
import com.joon.ringout.presentation.alarmsetup.toAlarmTimePickerValue
import com.joon.ringout.presentation.roomcreate.component.RoomCreateActionButton
import com.joon.ringout.presentation.roomcreate.component.RoomCreateHeader
import com.joon.ringout.presentation.roomcreate.component.RoomCreateIntroductionSection
import com.joon.ringout.presentation.roomcreate.component.RoomCreateNameSection
import com.joon.ringout.presentation.roomcreate.component.RoomCreateScheduleSection
import com.joon.ringout.ringoutColors
import com.joon.ringout.presentation.roomcreate.model.RoomCreateUiState

@Composable
internal fun RoomCreateScreen(
    uiState: RoomCreateUiState,
    onBackClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onIntroductionChange: (String) -> Unit,
    onDayClick: (String) -> Unit,
    onAmPmChange: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(uiState.step) {
        scrollState.animateScrollTo(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        RoomCreateHeader(onBackClick = onBackClick)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (uiState.step >= 3) {
                    RoomCreateScheduleSection(
                        selectedDays = uiState.selectedDays,
                        time = uiState.time24Hour?.toAlarmTimePickerValue()
                            ?: UninitializedTime,
                        onDayClick = onDayClick,
                        onAmPmChange = onAmPmChange,
                        onHourChange = onHourChange,
                        onMinuteChange = onMinuteChange,
                    )
                }
                if (uiState.step >= 2) {
                    RoomCreateIntroductionSection(
                        introduction = uiState.introduction,
                        validation = uiState.introductionValidation,
                        showErrors = uiState.step > 2,
                        onIntroductionChange = onIntroductionChange,
                    )
                }
                RoomCreateNameSection(
                    name = uiState.name,
                    validation = uiState.nameValidation,
                    showErrors = uiState.step > 1,
                    onNameChange = onNameChange,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            RoomCreateActionButton(
                label = if (uiState.step == 3) "모임 생성하기" else "다음으로",
                enabled = uiState.canContinue,
                onClick = onAction,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
    }
}

private val UninitializedTime = AlarmTimePickerValue(isAm = true, hour = 12, minute = 0)

private fun previewRoomCreateState(
    step: Int,
    name: String,
    introduction: String = "",
    selectedDays: List<String> = com.joon.ringout.presentation.common.WeekdayOrder,
) = RoomCreateUiState(
    step = step,
    name = name,
    introduction = introduction,
    selectedDays = selectedDays,
    time24Hour = "06:20",
)

@Composable
private fun RoomCreateScreenPreviewContent(
    themeMode: ThemeMode,
    uiState: RoomCreateUiState,
) {
    RingoutTheme(themeMode) {
        RoomCreateScreen(
            uiState = uiState,
            onBackClick = {},
            onNameChange = {},
            onIntroductionChange = {},
            onDayClick = {},
            onAmPmChange = {},
            onHourChange = {},
            onMinuteChange = {},
            onAction = {},
        )
    }
}

@Preview(name = "1단계 · 초기 · 라이트", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepOneInitialLightPreview() {
    RoomCreateScreenPreviewContent(ThemeMode.Light, previewRoomCreateState(step = 1, name = ""))
}

@Preview(name = "1단계 · 오류 · 라이트", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepOneErrorLightPreview() {
    RoomCreateScreenPreviewContent(ThemeMode.Light, previewRoomCreateState(step = 1, name = "가"))
}

@Preview(name = "1단계 · 유효 · 라이트", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepOneValidLightPreview() {
    RoomCreateScreenPreviewContent(ThemeMode.Light, previewRoomCreateState(step = 1, name = "가A2"))
}

@Preview(name = "1단계 · 초기 · 다크", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepOneInitialDarkPreview() {
    RoomCreateScreenPreviewContent(ThemeMode.Dark, previewRoomCreateState(step = 1, name = ""))
}

@Preview(name = "1단계 · 오류 · 다크", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepOneErrorDarkPreview() {
    RoomCreateScreenPreviewContent(ThemeMode.Dark, previewRoomCreateState(step = 1, name = "가"))
}

@Preview(name = "1단계 · 유효 · 다크", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepOneValidDarkPreview() {
    RoomCreateScreenPreviewContent(ThemeMode.Dark, previewRoomCreateState(step = 1, name = "가A2"))
}

@Preview(name = "2단계 · 초기 · 라이트", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepTwoInitialLightPreview() {
    RoomCreateScreenPreviewContent(ThemeMode.Light, previewRoomCreateState(step = 2, name = "가A2"))
}

@Preview(name = "2단계 · 초과 · 라이트", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepTwoOverLimitLightPreview() {
    RoomCreateScreenPreviewContent(
        ThemeMode.Light,
        previewRoomCreateState(
            step = 2,
            name = "가A2",
            introduction = "가".repeat(301),
        ),
    )
}

@Preview(name = "2단계 · 유효 · 라이트", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepTwoValidLightPreview() {
    RoomCreateScreenPreviewContent(
        ThemeMode.Light,
        previewRoomCreateState(step = 2, name = "가A2", introduction = "함께 건강한 습관을 만들어요."),
    )
}

@Preview(name = "2단계 · 초기 · 다크", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepTwoInitialDarkPreview() {
    RoomCreateScreenPreviewContent(ThemeMode.Dark, previewRoomCreateState(step = 2, name = "가A2"))
}

@Preview(name = "2단계 · 초과 · 다크", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepTwoOverLimitDarkPreview() {
    RoomCreateScreenPreviewContent(
        ThemeMode.Dark,
        previewRoomCreateState(
            step = 2,
            name = "가A2",
            introduction = "가".repeat(301),
        ),
    )
}

@Preview(name = "2단계 · 유효 · 다크", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepTwoValidDarkPreview() {
    RoomCreateScreenPreviewContent(
        ThemeMode.Dark,
        previewRoomCreateState(step = 2, name = "가A2", introduction = "함께 건강한 습관을 만들어요."),
    )
}

@Preview(name = "3단계 · 전체 선택 · 라이트", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepThreeAllDaysLightPreview() {
    RoomCreateScreenPreviewContent(
        ThemeMode.Light,
        previewRoomCreateState(step = 3, name = "가A2", introduction = "함께 건강한 습관을 만들어요."),
    )
}

@Preview(name = "3단계 · 선택 없음 · 라이트", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepThreeNoDaysLightPreview() {
    RoomCreateScreenPreviewContent(
        ThemeMode.Light,
        previewRoomCreateState(
            step = 3,
            name = "가A2",
            introduction = "함께 건강한 습관을 만들어요.",
            selectedDays = emptyList(),
        ),
    )
}

@Preview(name = "3단계 · 일부 선택 · 라이트", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepThreePartialDaysLightPreview() {
    RoomCreateScreenPreviewContent(
        ThemeMode.Light,
        previewRoomCreateState(
            step = 3,
            name = "가A2",
            introduction = "함께 건강한 습관을 만들어요.",
            selectedDays = listOf("월", "수", "금"),
        ),
    )
}

@Preview(name = "3단계 · 전체 선택 · 다크", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepThreeAllDaysDarkPreview() {
    RoomCreateScreenPreviewContent(
        ThemeMode.Dark,
        previewRoomCreateState(step = 3, name = "가A2", introduction = "함께 건강한 습관을 만들어요."),
    )
}

@Preview(name = "3단계 · 선택 없음 · 다크", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepThreeNoDaysDarkPreview() {
    RoomCreateScreenPreviewContent(
        ThemeMode.Dark,
        previewRoomCreateState(
            step = 3,
            name = "가A2",
            introduction = "함께 건강한 습관을 만들어요.",
            selectedDays = emptyList(),
        ),
    )
}

@Preview(name = "3단계 · 일부 선택 · 다크", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomCreateStepThreePartialDaysDarkPreview() {
    RoomCreateScreenPreviewContent(
        ThemeMode.Dark,
        previewRoomCreateState(
            step = 3,
            name = "가A2",
            introduction = "함께 건강한 습관을 만들어요.",
            selectedDays = listOf("월", "수", "금"),
        ),
    )
}

@Preview(name = "3단계 · 낮은 화면", widthDp = 320, heightDp = 640, showBackground = true)
@Composable
private fun RoomCreateStepThreeNarrowPreview() {
    RoomCreateScreenPreviewContent(
        ThemeMode.Dark,
        previewRoomCreateState(step = 3, name = "가A2", introduction = "함께 건강한 습관을 만들어요."),
    )
}
