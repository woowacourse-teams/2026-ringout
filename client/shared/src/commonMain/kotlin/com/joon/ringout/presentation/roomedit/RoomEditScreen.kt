package com.joon.ringout.presentation.roomedit

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.presentation.roomedit.component.RoomEditScheduleSection
import com.joon.ringout.presentation.alarmsetup.toAlarmTimePickerValue
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomedit.component.RoomEditHeader
import com.joon.ringout.presentation.roomedit.component.RoomEditImageSection
import com.joon.ringout.presentation.roomedit.component.RoomEditIntroductionSection
import com.joon.ringout.presentation.roomedit.component.RoomEditNameSection
import com.joon.ringout.presentation.roomedit.component.RoomEditSaveButton
import com.joon.ringout.presentation.roomedit.model.RoomEditUiState
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

@Composable
internal fun RoomEditScreen(
    uiState: RoomEditUiState,
    selectedImage: androidx.compose.ui.graphics.ImageBitmap?,
    isImagePreviewAvailable: Boolean,
    imageError: String?,
    onBackClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onIntroductionChange: (String) -> Unit,
    onImageChangeClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDayClick: (String) -> Unit = {},
    onAmPmChange: (Boolean) -> Unit = {},
    onHourChange: (Int) -> Unit = {},
    onMinuteChange: (Int) -> Unit = {},
) {
    val original = checkNotNull(uiState.original)
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        RoomEditHeader(onBackClick = onBackClick, enabled = !uiState.isSaving)
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
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "대표 이미지",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    RoomEditImageSection(
                        room = original,
                        selectedImage = selectedImage,
                        onChangeClick = onImageChangeClick,
                        enabled = !uiState.isSaving,
                    )
                    imageError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                RoomEditNameSection(
                    value = uiState.nameInput,
                    validation = uiState.nameInputValidation,
                    onValueChange = onNameChange,
                    enabled = !uiState.isSaving,
                )
                RoomEditIntroductionSection(
                    value = uiState.introductionInput,
                    validation = uiState.introductionInputValidation,
                    onValueChange = onIntroductionChange,
                    enabled = !uiState.isSaving,
                )
                RoomEditScheduleSection(
                    selectedDays = uiState.selectedDays,
                    time = uiState.time24Hour.toAlarmTimePickerValue(),
                    onDayClick = onDayClick,
                    onAmPmChange = onAmPmChange,
                    onHourChange = onHourChange,
                    onMinuteChange = onMinuteChange,
                )
                uiState.saveErrorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            RoomEditSaveButton(
                enabled = uiState.canSave && isImagePreviewAvailable,
                isSaving = uiState.isSaving,
                onClick = onSaveClick,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
    }
}

private val RoomEditPreviewRoom = RoomUiModel(
    id = "preview-room",
    representativeImage = null,
    name = "아침러닝",
    description = "함께 달리며 건강한 습관을 만들어요.",
    createdAt = "2026-09-15T09:00:00",
    activityDays = listOf("월", "수", "금"),
    activityTimeText = "오전 8:00",
    participantCount = 3,
    isJoined = true,
)

private fun previewRoomEditState(
    nameInput: String = RoomEditPreviewRoom.name,
    introductionInput: String = RoomEditPreviewRoom.description,
) = RoomEditUiState(
    roomId = RoomEditPreviewRoom.id,
    original = RoomEditPreviewRoom,
    selectedDays = RoomEditPreviewRoom.activityDays,
    time24Hour = "08:00",
    originalTime24Hour = "08:00",
    nameInput = nameInput,
    introductionInput = introductionInput,
)

@Composable
private fun RoomEditScreenPreviewContent(themeMode: ThemeMode, uiState: RoomEditUiState) {
    RingoutTheme(themeMode) {
        RoomEditScreen(
            uiState = uiState,
            selectedImage = null,
            isImagePreviewAvailable = true,
            imageError = null,
            onBackClick = {},
            onNameChange = {},
            onIntroductionChange = {},
            onImageChangeClick = {},
            onSaveClick = {},
        )
    }
}

@Preview(name = "모임 수정 · 초기 · 라이트", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomEditInitialLightPreview() {
    RoomEditScreenPreviewContent(ThemeMode.Light, previewRoomEditState())
}

@Preview(name = "모임 수정 · 초기 · 다크", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomEditInitialDarkPreview() {
    RoomEditScreenPreviewContent(ThemeMode.Dark, previewRoomEditState())
}

@Preview(name = "모임 수정 · 유효한 이름 · 라이트", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomEditValidPreview() {
    RoomEditScreenPreviewContent(ThemeMode.Light, previewRoomEditState(nameInput = "새러닝모임"))
}

@Preview(name = "모임 수정 · 무효한 소개 · 다크", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomEditInvalidPreview() {
    RoomEditScreenPreviewContent(
        ThemeMode.Dark,
        previewRoomEditState(nameInput = "새러닝모임", introductionInput = " "),
    )
}
