package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.Immutable

@Immutable
sealed interface MyPageAccountUiState {
    data object Loading : MyPageAccountUiState

    data object LoggedOut : MyPageAccountUiState

    data object Error : MyPageAccountUiState

    data class LoggedIn(
        val nickname: String,
        val email: String,
    ) : MyPageAccountUiState
}
