package com.joon.ringout.presentation.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.alarmactivity.AlarmActivityRepository
import com.joon.ringout.domain.alarmactivity.AlarmActivitySummary
import com.joon.ringout.domain.missionhistory.GetRecordsHistory
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.groupByAlarm
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.calendarDates
import com.joon.ringout.domain.missionhistory.plusDays
import com.joon.ringout.domain.missionhistory.weekDates
import com.joon.ringout.domain.missionhistory.yearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecordsViewModel(
    private val getRecordsHistory: GetRecordsHistory,
    initialDate: MissionDate,
    private val activityRepository: AlarmActivityRepository,
    private val currentDate: () -> MissionDate = { initialDate },
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RecordsUiState(today = initialDate, isLoading = true))
    val uiState: StateFlow<RecordsUiState> = mutableUiState.asStateFlow()

    private val scope = coroutineScope ?: viewModelScope
    private var summaryJob: Job? = null
    private var summaryRequestId = 0L
    private var weekLoadJob: Job? = null
    private var weekRequestId = 0L
    private var calendarLoadJob: Job? = null
    private var calendarRequestId = 0L
    private var weekHistory: List<AlarmUsageRecord> = emptyList()

    fun selectDate(date: MissionDate) {
        val previous = uiState.value
        closeCalendar()
        if (date == previous.selectedDate) return

        mutableUiState.update { state ->
            state.copy(
                selectedDate = date,
                weekDays = date.weekDates().map { recordsDayUiState(it, state.today, date) },
            )
        }
        observeSummary()
        if (previous.weekDays.map(RecordsDayUiState::date) == date.weekDates() &&
            !previous.isLoading && previous.errorMessage == null
        ) {
            showWeekHistory(weekHistory)
        } else {
            loadWeek()
        }
    }

    fun previousWeek() = selectDate(uiState.value.selectedDate.plusDays(-7))

    fun nextWeek() = selectDate(uiState.value.selectedDate.plusDays(7))

    fun openCalendar() {
        mutableUiState.update { state ->
            state.copy(isCalendarVisible = true, calendarMonth = state.selectedDate.yearMonth)
        }
        loadCalendar()
    }

    fun closeCalendar() {
        calendarRequestId++
        calendarLoadJob?.cancel()
        calendarLoadJob = null
        mutableUiState.update { it.copy(isCalendarVisible = false, isCalendarLoading = false) }
    }

    fun previousMonth() = selectMonth(uiState.value.calendarMonth.previous())

    fun nextMonth() = selectMonth(uiState.value.calendarMonth.next())

    fun retry() {
        observeSummary()
        loadWeek()
    }

    fun retryCalendar() {
        if (uiState.value.isCalendarVisible) loadCalendar()
    }

    /** Called whenever the screen becomes visible, including after a background mission finishes. */
    fun refresh() {
        val today = currentDate()
        mutableUiState.update { it.copy(today = today) }
        observeSummary()
        loadWeek()
        if (uiState.value.isCalendarVisible) loadCalendar()
    }

    private fun observeSummary() {
        val requestId = ++summaryRequestId
        summaryJob?.cancel()
        val date = uiState.value.selectedDate
        mutableUiState.update {
            it.copy(activitySummary = AlarmActivitySummary(), isSummaryLoading = true, summaryErrorMessage = null)
        }
        summaryJob = scope.launch {
            try {
                activityRepository.observeSummary(date).collect { summary ->
                    if (requestId == summaryRequestId) {
                        mutableUiState.update { it.copy(activitySummary = summary, isSummaryLoading = false) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (requestId == summaryRequestId) {
                    mutableUiState.update {
                        it.copy(isSummaryLoading = false, summaryErrorMessage = "알람 집계를 불러오지 못했어요.")
                    }
                }
            }
        }
    }

    private fun selectMonth(month: MissionYearMonth) {
        mutableUiState.update { it.copy(calendarMonth = month) }
        if (uiState.value.isCalendarVisible) loadCalendar()
    }

    private fun loadWeek() {
        val requestId = ++weekRequestId
        weekLoadJob?.cancel()
        val state = uiState.value
        val dates = state.selectedDate.weekDates()
        weekHistory = emptyList()
        mutableUiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                records = emptyList(),
                weekDays = dates.map { date -> recordsDayUiState(date, it.today, it.selectedDate) },
            )
        }
        weekLoadJob = scope.launch {
            try {
                getRecordsHistory.observe(dates, state.today).collect { history ->
                    if (requestId != weekRequestId) return@collect
                    weekHistory = history
                    showWeekHistory(history)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (requestId != weekRequestId) return@launch
                mutableUiState.update {
                    it.copy(isLoading = false, errorMessage = RecordsLoadErrorMessage)
                }
            }
        }
    }

    private fun showWeekHistory(history: List<AlarmUsageRecord>) {
        val historyByDate = history.groupBy(AlarmUsageRecord::date)
        mutableUiState.update { state ->
            state.copy(
                isLoading = false,
                errorMessage = null,
                records = historyByDate[state.selectedDate].orEmpty().groupByAlarm(),
                weekDays = state.selectedDate.weekDates().map { date ->
                    recordsDayUiState(date, state.today, state.selectedDate, historyByDate[date].orEmpty())
                },
            )
        }
    }

    private fun loadCalendar() {
        val requestId = ++calendarRequestId
        calendarLoadJob?.cancel()
        val state = uiState.value
        val dates = state.calendarMonth.calendarDates()
        mutableUiState.update {
            it.copy(
                isCalendarLoading = true,
                calendarErrorMessage = null,
                calendarDays = dates.map { date ->
                    date?.let { recordsDayUiState(it, state.today, state.selectedDate) }
                },
            )
        }
        calendarLoadJob = scope.launch {
            try {
                val historyByDate = getRecordsHistory(dates.filterNotNull(), state.today)
                    .groupBy(AlarmUsageRecord::date)
                if (requestId != calendarRequestId) return@launch
                mutableUiState.update { current ->
                    current.copy(
                        isCalendarLoading = false,
                        calendarDays = dates.map { date ->
                            date?.let {
                                recordsDayUiState(
                                    date = it,
                                    today = current.today,
                                    selectedDate = current.selectedDate,
                                    records = historyByDate[it].orEmpty(),
                                )
                            }
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (requestId != calendarRequestId) return@launch
                mutableUiState.update {
                    it.copy(isCalendarLoading = false, calendarErrorMessage = RecordsLoadErrorMessage)
                }
            }
        }
    }
}

internal const val RecordsLoadErrorMessage = "저장된 알람 사용 기록을 불러오지 못했어요."
