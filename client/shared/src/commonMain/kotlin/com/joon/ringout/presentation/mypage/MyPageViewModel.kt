package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.StampMonthChangeDirection
import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class MyPageUiState(
    val isLoading: Boolean = true,
    val selectedMonth: MyPageCalendarMonth,
    val successDates: Set<MissionDate> = emptySet(),
    val errorMessage: String? = null,
)

class MyPageViewModel(
    private val getMissionSuccessDates: GetMissionSuccessDates,
    private val productAnalyticsRecorder: ProductAnalyticsRecorder,
    initialMonth: MissionYearMonth,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    var uiState by mutableStateOf(
        MyPageUiState(selectedMonth = MyPageCalendarMonth(initialMonth)),
    )
        private set

    private var loadJob: Job? = null
    private var requestId = 0L
    private val scope = coroutineScope ?: viewModelScope
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
        load(entryMonth, CalendarLoadReason.ScreenEntry)
    }

    fun retry() {
        if (entryContext == null) return
        load(uiState.selectedMonth.value, CalendarLoadReason.Retry)
    }

    private fun selectMonth(
        month: MissionYearMonth,
        direction: StampMonthChangeDirection,
        loginState: AnalyticsLoginState,
    ) {
        val currentEntry = entryContext ?: return
        currentEntry.isCalendarViewedPending = false
        uiState = MyPageUiState(selectedMonth = MyPageCalendarMonth(month))
        runCatching {
            productAnalyticsRecorder.recordStampMonthChanged(
                direction = direction,
                year = month.year,
                month = month.month,
                loginState = loginState,
            )
        }
        load(month, CalendarLoadReason.MonthChange)
    }

    private fun load(
        month: MissionYearMonth,
        reason: CalendarLoadReason,
    ) {
        loadJob?.cancel()
        val currentRequestId = ++requestId
        uiState = uiState.copy(
            isLoading = true,
            successDates = emptySet(),
            errorMessage = null,
        )
        loadJob = scope.launch {
            runCatching { getMissionSuccessDates(month) }
                .onSuccess { successDates ->
                    if (currentRequestId == requestId && uiState.selectedMonth.value == month) {
                        uiState = uiState.copy(
                            isLoading = false,
                            successDates = successDates,
                        )
                        recordCalendarViewedIfNeeded(month, reason)
                    }
                }
                .onFailure { error ->
                    if (currentRequestId == requestId && uiState.selectedMonth.value == month) {
                        uiState = uiState.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "미션 기록을 불러오지 못했어요.",
                        )
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
