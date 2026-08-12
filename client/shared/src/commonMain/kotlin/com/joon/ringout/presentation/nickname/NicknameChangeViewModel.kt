package com.joon.ringout.presentation.nickname

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

@Immutable
internal data class NicknameChangeUiState(
    val nickname: String,
    val validation: NicknameValidation,
)

internal class NicknameChangeViewModel(
    initialNickname: String,
) : ViewModel() {
    var uiState by mutableStateOf(initialNickname.toNicknameChangeUiState())
        private set

    fun onNicknameChange(nickname: String) {
        uiState = nickname.toNicknameChangeUiState()
    }

    private fun String.toNicknameChangeUiState(): NicknameChangeUiState =
        NicknameChangeUiState(
            nickname = this,
            validation = validateNickname(this),
        )
}
