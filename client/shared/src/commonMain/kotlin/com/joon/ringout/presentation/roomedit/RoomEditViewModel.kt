package com.joon.ringout.presentation.roomedit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.joon.ringout.presentation.roomedit.model.RoomEditDraft
import com.joon.ringout.presentation.roomedit.model.RoomEditImageChange
import com.joon.ringout.presentation.roomedit.model.RoomEditUiState
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

internal class RoomEditViewModel : ViewModel() {
    var uiState by mutableStateOf(RoomEditUiState())
        private set

    fun initialize(roomId: String, original: RoomUiModel?) {
        if (uiState.roomId != roomId) {
            uiState = RoomEditUiState(roomId = roomId)
        }
        if (!uiState.isOriginalLoaded && original?.id == roomId) {
            uiState = uiState.copy(
                original = original,
                nameInput = original.name,
                introductionInput = original.description,
            )
        }
    }

    fun updateName(value: String) {
        if (uiState.isOriginalLoaded) uiState = uiState.copy(nameInput = value)
    }

    fun updateIntroduction(value: String) {
        if (uiState.isOriginalLoaded) uiState = uiState.copy(introductionInput = value)
    }

    fun onImageSelected(selectionToken: Long) {
        if (uiState.isOriginalLoaded) {
            uiState = uiState.copy(imageSelectionToken = selectionToken)
        }
    }

    fun onImagePreviewLost(selectionToken: Long) {
        if (uiState.imageSelectionToken == selectionToken) {
            uiState = uiState.copy(imageSelectionToken = null)
        }
    }

    fun createDraft(): RoomEditDraft? {
        val state = uiState
        val original = state.original ?: return null
        if (!state.canSave) return null

        return RoomEditDraft(
            roomId = checkNotNull(state.roomId),
            name = if (state.nameChanged) state.effectiveNameValidation.normalizedValue else original.name,
            introduction = state.effectiveIntroduction,
            imageChange = state.imageSelectionToken?.let(RoomEditImageChange::Replace)
                ?: RoomEditImageChange.Unchanged,
        )
    }
}
