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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class RecordsViewModel(
    private val getRecordsHistory: GetRecordsHistory,
    initialDate: MissionDate,
    private val activityRepository: AlarmActivityRepository,
    private val currentDate: () -> MissionDate = { initialDate },
    coroutineScope: CoroutineScope? = null,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RecordsUiState(today = initialDate, isLoading = true, isSummaryLoading = true))
    val uiState: StateFlow<RecordsUiState> = mutableUiState.asStateFlow()

    private val scope = coroutineScope ?: viewModelScope
    private var summaryJob: Job? = null
    private var summaryRequestId = 0L
    private var summaryDates: List<MissionDate> = emptyList()
    private var weekSummaries: Map<MissionDate, AlarmActivitySummary> = emptyMap()
    private var weekLoadJob: Job? = null
    private var weekRequestId = 0L
    private var calendarLoadJob: Job? = null
    private var calendarRequestId = 0L
    private var weekHistory: List<AlarmUsageRecord> = emptyList()
    private var requestedDate: MissionDate = initialDate
    private var weekChangeRetryDate: MissionDate? = null

    fun selectDate(date: MissionDate) {
        val previous = uiState.value
        closeCalendar()
        if (date == requestedDate && (previous.isWeekChanging || date == previous.selectedDate)) return
        if (previous.isWeekChanging && date.weekDates() == requestedDate.weekDates()) {
            requestedDate = date
            return
        }
        requestedDate = date
        if (previous.isWeekChanging || date.weekDates() != previous.selectedDate.weekDates() || weekChangeRetryDate != null) {
            changeWeek(date)
            return
        }

        mutableUiState.update { state ->
            state.copy(
                selectedDate = date,
                weekDays = date.weekDates().map { recordsDayUiState(it, state.today, date) },
            )
        }
        observeWeekSummaries()
        val isSameWeek = previous.weekDays.map(RecordsDayUiState::date) == date.weekDates()
        if (isSameWeek &&
            !previous.isLoading && previous.errorMessage == null
        ) {
            showWeekHistory(weekHistory)
        } else if (!isSameWeek || weekLoadJob?.isActive != true) {
            loadWeek()
        }
    }

    fun previousWeek() = selectDate(requestedDate.plusDays(-7))

    fun nextWeek() = selectDate(requestedDate.plusDays(7))

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
        val retryDate = weekChangeRetryDate
        if (retryDate != null || uiState.value.isWeekChanging) {
            changeWeek(retryDate ?: requestedDate)
            return
        }
        observeWeekSummaries(forceReload = true)
        loadWeek()
    }

    fun retryCalendar() {
        if (uiState.value.isCalendarVisible) loadCalendar()
    }

    /** Called whenever the screen becomes visible, including after a background mission finishes. */
    fun refresh() {
        val today = currentDate()
        mutableUiState.update { it.copy(today = today) }
        if (uiState.value.isWeekChanging) {
            changeWeek(requestedDate)
            return
        }
        weekChangeRetryDate = null
        mutableUiState.update { it.copy(weekChangeErrorMessage = null) }
        observeWeekSummaries(forceReload = true)
        loadWeek()
        if (uiState.value.isCalendarVisible) loadCalendar()
    }

    private fun changeWeek(date: MissionDate) {
        val requestId = ++weekRequestId
        weekLoadJob?.cancel()
        summaryRequestId++
        summaryJob?.cancel()
        requestedDate = date
        weekChangeRetryDate = null
        val dates = date.weekDates()
        val startedAt = timeSource.markNow()
        mutableUiState.update {
            it.copy(
                isWeekChanging = true,
                showWeekLoadingIndicator = false,
                weekChangeErrorMessage = null,
                isLoading = false,
                isSummaryLoading = false,
                showSummaryLoadingIndicator = false,
            )
        }
        weekLoadJob = scope.launch {
            val loadingIndicatorJob = launch {
                delay((SummaryLoadingIndicatorDelayMillis.milliseconds - startedAt.elapsedNow()).coerceAtLeast(Duration.ZERO))
                if (requestId == weekRequestId && uiState.value.isWeekChanging) {
                    mutableUiState.update { it.copy(showWeekLoadingIndicator = true) }
                }
            }
            var hasDisplayedWeek = false
            try {
                combine(
                    getRecordsHistory.observe(dates, uiState.value.today),
                    activityRepository.observeSummaries(dates),
                ) { history, summaries -> history to summaries }.collectLatest { (history, summaries) ->
                    if (requestId != weekRequestId) return@collectLatest
                    if (!hasDisplayedWeek) awaitWeekLoadingMinimum(startedAt)
                    if (requestId != weekRequestId) return@collectLatest
                    loadingIndicatorJob.cancel()
                    val selectedDate = if (hasDisplayedWeek) uiState.value.selectedDate else requestedDate
                    val historyByDate = history.groupBy(AlarmUsageRecord::date)
                    weekHistory = history
                    weekSummaries = summaries
                    summaryDates = dates
                    hasDisplayedWeek = true
                    mutableUiState.update { state ->
                        state.copy(
                            selectedDate = selectedDate,
                            records = historyByDate[selectedDate].orEmpty().groupByAlarm(),
                            weekDays = dates.map { day ->
                                recordsDayUiState(day, state.today, selectedDate, historyByDate[day].orEmpty())
                            },
                            activitySummary = summaries.getValue(selectedDate),
                            isLoading = false,
                            errorMessage = null,
                            isSummaryLoading = false,
                            summaryErrorMessage = null,
                            isWeekChanging = false,
                            showWeekLoadingIndicator = false,
                            weekChangeErrorMessage = null,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (requestId != weekRequestId) return@launch
                if (!hasDisplayedWeek) awaitWeekLoadingMinimum(startedAt)
                if (requestId != weekRequestId) return@launch
                weekChangeRetryDate = requestedDate
                requestedDate = uiState.value.selectedDate
                mutableUiState.update {
                    it.copy(
                        isWeekChanging = false,
                        showWeekLoadingIndicator = false,
                        weekChangeErrorMessage = RecordsLoadErrorMessage,
                    )
                }
            } finally {
                loadingIndicatorJob.cancel()
            }
        }
    }

    private suspend fun awaitWeekLoadingMinimum(startedAt: TimeMark) {
        val elapsed = startedAt.elapsedNow()
        if (uiState.value.showWeekLoadingIndicator && elapsed > SummaryLoadingIndicatorDelayMillis.milliseconds) {
            delay((WeekLoadingMinimumEndMillis.milliseconds - elapsed).coerceAtLeast(Duration.ZERO))
        }
    }

    private fun observeWeekSummaries(forceReload: Boolean = false) {
        val dates = uiState.value.selectedDate.weekDates()
        if (summaryDates == dates && !forceReload) {
            mutableUiState.update {
                it.copy(activitySummary = weekSummaries[it.selectedDate] ?: AlarmActivitySummary())
            }
            return
        }
        val requestId = ++summaryRequestId
        summaryJob?.cancel()
        if (summaryDates != dates) weekSummaries = emptyMap()
        summaryDates = dates
        mutableUiState.update {
            it.copy(
                activitySummary = weekSummaries[it.selectedDate] ?: AlarmActivitySummary(),
                isSummaryLoading = true,
                showSummaryLoadingIndicator = false,
                summaryErrorMessage = null,
            )
        }
        summaryJob = scope.launch {
            val loadingIndicatorJob = launch {
                delay(SummaryLoadingIndicatorDelayMillis)
                if (requestId == summaryRequestId && uiState.value.isSummaryLoading) {
                    mutableUiState.update { it.copy(showSummaryLoadingIndicator = true) }
                }
            }
            try {
                activityRepository.observeSummaries(dates).collect { summaries ->
                    if (requestId == summaryRequestId) {
                        loadingIndicatorJob.cancel()
                        weekSummaries = summaries
                        mutableUiState.update {
                            it.copy(
                                activitySummary = summaries.getValue(it.selectedDate),
                                isSummaryLoading = false,
                                showSummaryLoadingIndicator = false,
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (requestId == summaryRequestId) {
                    mutableUiState.update {
                        it.copy(
                            isSummaryLoading = false,
                            showSummaryLoadingIndicator = false,
                            summaryErrorMessage = "알람 집계를 불러오지 못했어요.",
                        )
                    }
                }
            } finally {
                loadingIndicatorJob.cancel()
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
private const val SummaryLoadingIndicatorDelayMillis = 500L
private const val WeekLoadingMinimumEndMillis = 750L
