package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.Immutable

@Immutable
sealed interface MyPageAccountUiState {
    data object Loading : MyPageAccountUiState

    data object LoggedOut : MyPageAccountUiState

    data class LoggedIn(
        val nickname: String = "로그인됨",
        val email: String = "회원 정보는 준비 중이에요.",
    ) : MyPageAccountUiState
}
