package com.joon.ringout.presentation.roomhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.plusDays
import com.joon.ringout.domain.missionhistory.weekDates
import com.joon.ringout.domain.missionhistory.yearMonth
import com.joon.ringout.domain.room.RoomScheduleClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 전달받은 모임 정보와 기기 시각으로 화면 상태를 관리한다. 조회 API는 연결하지 않는다. */
internal class RoomHomeViewModel(
    initialState: RoomHomeUiState = RoomHomeUiState(),
    recordsByDate: Map<MissionDate, RoomHomeDayRecordsUiModel> = mapOf(
        initialState.recordsState.selectedDate to RoomHomeDayRecordsUiModel(
            records = initialState.recordsState.records,
            achievedMemberCount = initialState.recordsState.achievedMemberCount,
        ),
    ),
    private val clock: RoomScheduleClock = systemRoomScheduleClock(),
    private val coroutineScope: CoroutineScope? = null,
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
        ).withCurrentSchedule(clock),
    )
    val uiState = mutableUiState.asStateFlow()
    private var countdownJob: Job? = null

    /** 화면 복귀 시에도 저장된 초를 감소시키지 않고 실제 현재 시각으로 다시 계산한다. */
    fun startCountdown() {
        mutableUiState.update { it.withCurrentSchedule(clock) }
        if (countdownJob?.isActive == true || uiState.value.room?.toActivitySchedule()?.days.isNullOrEmpty()) return
        countdownJob = (coroutineScope ?: viewModelScope).launch {
            while (isActive) {
                delay(1_000)
                mutableUiState.update { it.withCurrentSchedule(clock) }
            }
        }
    }

    fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

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
