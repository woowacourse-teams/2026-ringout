package com.joon.ringout.presentation.roomhome

import androidx.lifecycle.ViewModel
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.plusDays
import com.joon.ringout.domain.missionhistory.weekDates
import com.joon.ringout.domain.missionhistory.yearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 전달받은 정보만 표시하는 UI 단계의 ViewModel. 조회 API나 일정 계산을 수행하지 않는다. */
internal class RoomHomeViewModel(
    initialState: RoomHomeUiState = RoomHomeUiState(),
    recordsByDate: Map<MissionDate, RoomHomeDayRecordsUiModel> = mapOf(
        initialState.recordsState.selectedDate to RoomHomeDayRecordsUiModel(
            records = initialState.recordsState.records,
            achievedMemberCount = initialState.recordsState.achievedMemberCount,
        ),
    ),
) : ViewModel() {
    // UI 확인을 위해 전달된 메모리 데이터만 사용한다. 서버/개인 기록 저장소에 접근하지 않는다.
    private val canViewRecords = initialState.room?.isJoined == true && initialState.recordsState.canViewRecords
    private val localRecords = if (canViewRecords) {
        recordsByDate.mapValues { (_, value) -> value.copy(records = value.records.toList()) }
    } else emptyMap()
    private val mutableUiState = MutableStateFlow(
        initialState.copy(
            recordsState = initialState.recordsState.copy(
                canViewRecords = canViewRecords,
                records = localRecords[initialState.recordsState.selectedDate]?.records.orEmpty(),
                achievedMemberCount = localRecords[initialState.recordsState.selectedDate]?.achievedMemberCount ?: 0,
                participantCounts = localRecords.mapValues { it.value.achievedMemberCount },
            ),
            isCalendarVisible = initialState.isCalendarVisible && canViewRecords,
        ),
    )
    val uiState = mutableUiState.asStateFlow()

    fun onTabSelected(tab: RoomHomeTab) {
        mutableUiState.update { it.copy(selectedTab = tab, isCalendarVisible = false) }
    }

    fun onDateSelected(date: MissionDate) {
        mutableUiState.update { state ->
            val day = localRecords[date] ?: RoomHomeDayRecordsUiModel()
            state.copy(
                recordsState = state.recordsState.copy(
                    selectedDate = date,
                    visibleWeekStart = date.weekDates().first(),
                    records = day.records,
                    achievedMemberCount = day.achievedMemberCount,
                    participantCounts = localRecords.mapValues { it.value.achievedMemberCount },
                    isLoading = false,
                    errorMessage = null,
                ),
                calendarMonth = date.yearMonth,
                isCalendarVisible = false,
            )
        }
    }

    fun onPreviousWeek() = onDateSelected(uiState.value.recordsState.selectedDate.plusDays(-7))

    fun onNextWeek() = onDateSelected(uiState.value.recordsState.selectedDate.plusDays(7))

    fun onOpenCalendar() {
        mutableUiState.update {
            it.copy(isCalendarVisible = canViewRecords, calendarMonth = it.recordsState.selectedDate.yearMonth)
        }
    }

    fun onDismissCalendar() {
        mutableUiState.update { it.copy(isCalendarVisible = false) }
    }

    fun onPreviousMonth() {
        mutableUiState.update { it.copy(calendarMonth = it.calendarMonth.previous()) }
    }

    fun onNextMonth() {
        mutableUiState.update { it.copy(calendarMonth = it.calendarMonth.next()) }
    }

    /** 현재 선택 날짜의 메모리 데이터를 다시 표시한다. 네트워크 새로고침은 후속 API 작업에서 연결한다. */
    fun onRefresh() = onDateSelected(uiState.value.recordsState.selectedDate)
}
