package com.joon.ringout.presentation.roomedit.model

import androidx.compose.runtime.Immutable
import com.joon.ringout.domain.room.RoomUpdateResult
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

@Immutable
internal data class RoomEditUiState(
    val roomId: String? = null,
    val original: RoomUiModel? = null,
    val nameInput: String = "",
    val introductionInput: String = "",
    val selectedDays: List<String> = emptyList(),
    val time24Hour: String = "00:00",
    val originalTime24Hour: String = "00:00",
    val imageSelectionToken: Long? = null,
    val isLoading: Boolean = false,
    val loadErrorMessage: String? = null,
    val canRetryLoad: Boolean = false,
    val isSaving: Boolean = false,
    val saveErrorMessage: String? = null,
    val isSaveBlocked: Boolean = false,
    val successfulUpdate: RoomEditSuccessfulUpdate? = null,
) {
    val isOriginalLoaded: Boolean
        get() = original != null && original.id == roomId

    val nameInputValidation: RoomEditNameValidation
        get() = validateRoomEditName(nameInput)

    val introductionInputValidation: RoomEditDescriptionValidation
        get() = validateRoomEditDescription(introductionInput)

    val effectiveName: String
        get() = nameInput

    val effectiveIntroduction: String
        get() = introductionInput

    val effectiveNameValidation: RoomEditNameValidation
        get() = validateRoomEditName(effectiveName)

    val effectiveIntroductionValidation: RoomEditDescriptionValidation
        get() = validateRoomEditDescription(effectiveIntroduction)

    val nameChanged: Boolean
        get() = isOriginalLoaded &&
            effectiveNameValidation.normalizedValue != validateRoomEditName(checkNotNull(original).name).normalizedValue

    val introductionChanged: Boolean
        get() = isOriginalLoaded && effectiveIntroduction != checkNotNull(original).description

    val scheduleChanged: Boolean
        get() = isOriginalLoaded &&
            (selectedDays.toSet() != original?.activityDays?.toSet() || time24Hour != originalTime24Hour)

    val hasChanges: Boolean
        get() = nameChanged || introductionChanged || imageSelectionToken != null

    val canSave: Boolean
        get() = isOriginalLoaded && !isSaving && !isSaveBlocked && hasChanges &&
            (!nameChanged || effectiveNameValidation.isValid) &&
            (!introductionChanged || effectiveIntroductionValidation.isValid)

    val completionId: Long?
        get() = successfulUpdate?.completionId
}

@Immutable
internal data class RoomEditSuccessfulUpdate(
    val completionId: Long,
    val result: RoomUpdateResult,
    val sessionIdentity: Any,
)
