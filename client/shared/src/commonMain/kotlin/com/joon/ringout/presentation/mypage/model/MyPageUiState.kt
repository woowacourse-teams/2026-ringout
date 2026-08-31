package com.joon.ringout.presentation.mypage.model

import androidx.compose.runtime.Immutable
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.mypage.MyPageCalendarMonth

@Immutable
data class MyPageUiState(
    val isLoading: Boolean = true,
    val selectedMonth: MyPageCalendarMonth,
    val successDates: Set<MissionDate> = emptySet(),
    val errorMessage: String? = null,
    val accountStatus: MyPageAccountStatus = MyPageAccountStatus.Loading,
    val accountAction: MyPageAccountActionState = MyPageAccountActionState.Idle,
)

@Immutable
sealed interface MyPageAccountStatus {
    data object Loading : MyPageAccountStatus

    data object LoggedOut : MyPageAccountStatus

    data object Error : MyPageAccountStatus

    data class LoggedIn(
        val nickname: String,
        val email: String,
    ) : MyPageAccountStatus
}

@Immutable
sealed interface MyPageAccountActionState {
    data object Idle : MyPageAccountActionState

    data class InProgress(
        val action: MyPageAccountAction,
    ) : MyPageAccountActionState

    data class Error(
        val action: MyPageAccountAction,
        val message: String,
    ) : MyPageAccountActionState

    data class Completed(
        val eventId: Long,
        val action: MyPageAccountAction,
    ) : MyPageAccountActionState
}
