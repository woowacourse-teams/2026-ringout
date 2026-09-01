package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.StampMonthChangeDirection
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.member.MemberRepository
import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.presentation.mypage.model.MyPageAccountAction
import com.joon.ringout.presentation.mypage.model.MyPageAccountActionState
import com.joon.ringout.presentation.mypage.model.MyPageAccountStatus
import com.joon.ringout.presentation.mypage.model.MyPageUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MyPageViewModel(
    private val getMissionSuccessDates: GetMissionSuccessDates,
    private val memberRepository: MemberRepository,
    private val authRepository: AuthRepository,
    private val productAnalyticsRecorder: ProductAnalyticsRecorder,
    initialMonth: MissionYearMonth,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    var uiState by mutableStateOf(
        MyPageUiState(selectedMonth = MyPageCalendarMonth(initialMonth)),
    )
        private set

    private val scope = coroutineScope ?: viewModelScope
    private var calendarLoadJob: Job? = null
    private var calendarRequestId = 0L
    private var profileLoadJob: Job? = null
    private var profileRequestId = 0L
    private var accountActionJob: Job? = null
    private var accountActionRequestId = 0L
    private var nextAccountActionEventId = 0L
    private var entryContext: MyPageEntryContext? = null

    fun onPreviousMonthClick(loginState: AnalyticsLoginState) = selectMonth(
        month = uiState.selectedMonth.value.previous(),
        direction = StampMonthChangeDirection.Previous,
        loginState = loginState,
    )

    fun onNextMonthClick(loginState: AnalyticsLoginState) = selectMonth(
        month = uiState.selectedMonth.value.next(),
        direction = StampMonthChangeDirection.Next,
        loginState = loginState,
    )

    /** Reloads the selected month once for each visible My Page composition. */
    internal fun onScreenEntered(
        entryToken: MyPageEntryToken,
        loginState: AnalyticsLoginState,
    ) {
        if (entryContext?.entryToken === entryToken) return

        val entryMonth = uiState.selectedMonth.value
        entryContext = MyPageEntryContext(
            entryToken = entryToken,
            entryMonth = entryMonth,
            entryLoginState = loginState,
        )
        loadCalendar(entryMonth, CalendarLoadReason.ScreenEntry)
    }

    fun retryCalendar() {
        if (entryContext == null) return
        loadCalendar(uiState.selectedMonth.value, CalendarLoadReason.Retry)
    }

    fun onSessionRestoring() {
        cancelProfileLoad()
        uiState = uiState.copy(accountStatus = MyPageAccountStatus.Loading)
    }

    fun onAuthenticated() {
        loadProfile()
    }

    fun onLoggedOut() {
        cancelProfileLoad()
        uiState = uiState.copy(accountStatus = MyPageAccountStatus.LoggedOut)
    }

    fun retryAccount() {
        if (uiState.accountStatus != MyPageAccountStatus.Error) return
        loadProfile()
    }

    fun onNicknameUpdated(nickname: String) {
        val profile = uiState.accountStatus as? MyPageAccountStatus.LoggedIn ?: return
        uiState = uiState.copy(
            accountStatus = profile.copy(nickname = nickname),
        )
    }

    fun logout() {
        performAccountAction(MyPageAccountAction.Logout) {
            authRepository.logout()
        }
    }

    fun withdraw() {
        performAccountAction(MyPageAccountAction.Withdraw) {
            memberRepository.withdraw()
            runCatching { productAnalyticsRecorder.recordAccountWithdrawalCompleted() }
            authRepository.logout()
        }
    }

    fun clearAccountActionError() {
        if (uiState.accountAction is MyPageAccountActionState.Error) {
            uiState = uiState.copy(accountAction = MyPageAccountActionState.Idle)
        }
    }

    fun consumeAccountActionCompletedEvent(eventId: Long) {
        val completed = uiState.accountAction as? MyPageAccountActionState.Completed ?: return
        if (completed.eventId == eventId) {
            uiState = uiState.copy(accountAction = MyPageAccountActionState.Idle)
        }
    }

    fun resetAccountActionFlow() {
        accountActionRequestId++
        accountActionJob?.cancel()
        accountActionJob = null
        uiState = uiState.copy(accountAction = MyPageAccountActionState.Idle)
    }

    private fun selectMonth(
        month: MissionYearMonth,
        direction: StampMonthChangeDirection,
        loginState: AnalyticsLoginState,
    ) {
        val currentEntry = entryContext ?: return
        currentEntry.isCalendarViewedPending = false
        uiState = uiState.copy(selectedMonth = MyPageCalendarMonth(month))
        runCatching {
            productAnalyticsRecorder.recordStampMonthChanged(
                direction = direction,
                year = month.year,
                month = month.month,
                loginState = loginState,
            )
        }
        loadCalendar(month, CalendarLoadReason.MonthChange)
    }

    private fun loadCalendar(
        month: MissionYearMonth,
        reason: CalendarLoadReason,
    ) {
        calendarLoadJob?.cancel()
        val currentRequestId = ++calendarRequestId
        uiState = uiState.copy(
            isLoading = true,
            successDates = emptySet(),
            errorMessage = null,
        )
        calendarLoadJob = scope.launch {
            runCatching { getMissionSuccessDates(month) }
                .onSuccess { successDates ->
                    if (
                        currentRequestId == calendarRequestId &&
                        uiState.selectedMonth.value == month
                    ) {
                        uiState = uiState.copy(
                            isLoading = false,
                            successDates = successDates,
                        )
                        recordCalendarViewedIfNeeded(month, reason)
                    }
                }
                .onFailure { error ->
                    if (
                        currentRequestId == calendarRequestId &&
                        uiState.selectedMonth.value == month
                    ) {
                        uiState = uiState.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "미션 기록을 불러오지 못했어요.",
                        )
                    }
                }
        }
    }

    private fun loadProfile() {
        profileLoadJob?.cancel()
        val currentRequestId = ++profileRequestId
        uiState = uiState.copy(accountStatus = MyPageAccountStatus.Loading)
        profileLoadJob = scope.launch {
            try {
                val profile = memberRepository.getProfile()
                if (currentRequestId != profileRequestId) return@launch
                uiState = uiState.copy(
                    accountStatus = MyPageAccountStatus.LoggedIn(
                        nickname = profile.nickname,
                        email = profile.email?.takeIf { it.isNotBlank() } ?: MissingEmailMessage,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (currentRequestId == profileRequestId) {
                    uiState = uiState.copy(accountStatus = MyPageAccountStatus.Error)
                }
            }
        }
    }

    private fun cancelProfileLoad() {
        profileRequestId++
        profileLoadJob?.cancel()
        profileLoadJob = null
    }

    private fun performAccountAction(
        action: MyPageAccountAction,
        request: suspend () -> Unit,
    ) {
        when (uiState.accountAction) {
            is MyPageAccountActionState.InProgress,
            is MyPageAccountActionState.Completed,
            -> return

            MyPageAccountActionState.Idle,
            is MyPageAccountActionState.Error,
            -> Unit
        }

        val currentRequestId = ++accountActionRequestId
        uiState = uiState.copy(
            accountAction = MyPageAccountActionState.InProgress(action),
        )
        accountActionJob = scope.launch {
            try {
                request()
                if (currentRequestId != accountActionRequestId) return@launch
                uiState = uiState.copy(
                    accountAction = MyPageAccountActionState.Completed(
                        eventId = ++nextAccountActionEventId,
                        action = action,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (currentRequestId != accountActionRequestId) return@launch
                uiState = uiState.copy(
                    accountAction = MyPageAccountActionState.Error(
                        action = action,
                        message = error.message ?: action.defaultErrorMessage,
                    ),
                )
            } finally {
                if (currentRequestId == accountActionRequestId) {
                    accountActionJob = null
                }
            }
        }
    }

    private fun recordCalendarViewedIfNeeded(
        month: MissionYearMonth,
        reason: CalendarLoadReason,
    ) {
        if (reason == CalendarLoadReason.MonthChange) return

        val currentEntry = entryContext ?: return
        if (!currentEntry.isCalendarViewedPending || currentEntry.entryMonth != month) return

        currentEntry.isCalendarViewedPending = false
        runCatching {
            productAnalyticsRecorder.recordStampCalendarViewed(
                year = month.year,
                month = month.month,
                loginState = currentEntry.entryLoginState,
            )
        }
    }
}

internal class MyPageEntryToken

private class MyPageEntryContext(
    val entryToken: MyPageEntryToken,
    val entryMonth: MissionYearMonth,
    val entryLoginState: AnalyticsLoginState,
    var isCalendarViewedPending: Boolean = true,
)

private enum class CalendarLoadReason {
    ScreenEntry,
    MonthChange,
    Retry,
}

private val MyPageAccountAction.defaultErrorMessage: String
    get() = when (this) {
        MyPageAccountAction.Logout -> "로그아웃을 다시 시도해 주세요."
        MyPageAccountAction.Withdraw -> "회원 탈퇴를 다시 시도해 주세요."
    }

private const val MissingEmailMessage = "이메일 정보 없음"
