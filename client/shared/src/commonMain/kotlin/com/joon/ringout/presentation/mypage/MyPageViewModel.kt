package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun onPreviousMonthClick() = selectMonth(uiState.selectedMonth.value.previous())

    fun onNextMonthClick() = selectMonth(uiState.selectedMonth.value.next())

    /** Reloads the selected month whenever My Page becomes visible again. */
    fun onScreenEntered() = load(uiState.selectedMonth.value)

    fun retry() = load(uiState.selectedMonth.value)

    private fun selectMonth(month: MissionYearMonth) {
        uiState = MyPageUiState(selectedMonth = MyPageCalendarMonth(month))
        load(month)
    }

    private fun load(month: MissionYearMonth) {
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
}
