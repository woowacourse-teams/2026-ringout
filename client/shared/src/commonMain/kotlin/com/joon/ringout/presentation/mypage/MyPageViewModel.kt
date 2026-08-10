package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.missionhistory.GetMissionResultsByDate
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class MyPageUiState(
    val isLoading: Boolean = true,
    val selectedMonth: MyPageCalendarMonth,
    val resultsByDate: Map<MissionDate, MissionResult> = emptyMap(),
    val errorMessage: String? = null,
)

class MyPageViewModel(
    private val getMissionResultsByDate: GetMissionResultsByDate,
    initialMonth: MissionYearMonth,
) : ViewModel() {
    var uiState by mutableStateOf(
        MyPageUiState(selectedMonth = MyPageCalendarMonth(initialMonth)),
    )
        private set

    private var loadJob: Job? = null
    private var requestId = 0L

    init {
        load(initialMonth)
    }

    fun onPreviousMonthClick() = selectMonth(uiState.selectedMonth.value.previous())

    fun onNextMonthClick() = selectMonth(uiState.selectedMonth.value.next())

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
            resultsByDate = emptyMap(),
            errorMessage = null,
        )
        loadJob = viewModelScope.launch {
            runCatching { getMissionResultsByDate(month) }
                .onSuccess { resultsByDate ->
                    if (currentRequestId == requestId && uiState.selectedMonth.value == month) {
                        uiState = uiState.copy(
                            isLoading = false,
                            resultsByDate = resultsByDate,
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
