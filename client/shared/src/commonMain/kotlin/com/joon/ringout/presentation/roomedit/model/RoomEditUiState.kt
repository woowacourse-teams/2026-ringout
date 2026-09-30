package com.joon.ringout.presentation.roomedit.model

import androidx.compose.runtime.Immutable
import com.joon.ringout.presentation.roomcreate.RoomIntroductionValidation
import com.joon.ringout.presentation.roomcreate.RoomNameValidation
import com.joon.ringout.presentation.roomcreate.validateRoomIntroduction
import com.joon.ringout.presentation.roomcreate.validateRoomName
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

@Immutable
internal data class RoomEditUiState(
    val roomId: String? = null,
    val original: RoomUiModel? = null,
    val nameInput: String = "",
    val introductionInput: String = "",
    val imageSelectionToken: Long? = null,
) {
    val isOriginalLoaded: Boolean
        get() = original != null && original.id == roomId

    val nameInputValidation: RoomNameValidation
        get() = validateRoomName(nameInput)

    val introductionInputValidation: RoomIntroductionValidation
        get() = validateRoomIntroduction(introductionInput)

    val effectiveName: String
        get() = if (nameInput.isEmpty()) original?.name.orEmpty() else nameInput

    val effectiveIntroduction: String
        get() = if (introductionInput.isEmpty()) original?.description.orEmpty() else introductionInput

    val effectiveNameValidation: RoomNameValidation
        get() = validateRoomName(effectiveName)

    val effectiveIntroductionValidation: RoomIntroductionValidation
        get() = validateRoomIntroduction(effectiveIntroduction)

    val nameChanged: Boolean
        get() = isOriginalLoaded && nameInput.isNotEmpty() &&
            effectiveNameValidation.normalizedValue != validateRoomName(checkNotNull(original).name).normalizedValue

    val introductionChanged: Boolean
        get() = isOriginalLoaded && introductionInput.isNotEmpty() &&
            effectiveIntroduction != checkNotNull(original).description

    val hasChanges: Boolean
        get() = nameChanged || introductionChanged || imageSelectionToken != null

    val canSave: Boolean
        get() = isOriginalLoaded && hasChanges &&
            effectiveNameValidation.isValid && effectiveIntroductionValidation.isValid
}

@Immutable
internal sealed interface RoomEditImageChange {
    data object Unchanged : RoomEditImageChange
    data class Replace(val selectionToken: Long) : RoomEditImageChange
}

@Immutable
internal data class RoomEditDraft(
    val roomId: String,
    val name: String,
    val introduction: String,
    val imageChange: RoomEditImageChange,
)
