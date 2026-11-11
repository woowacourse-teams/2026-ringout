package com.joon.ringout.presentation.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.alarmactivity.AlarmActivityRepository
import com.joon.ringout.domain.alarmactivity.AlarmActivitySummary
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceMonthLoader
import com.joon.ringout.domain.alarmoccurrence.currentAlarmOccurrenceMonth
import com.joon.ringout.domain.missionhistory.GetRecordsHistory
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.groupByAlarm
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.RecordsHistorySnapshot
import com.joon.ringout.domain.missionhistory.calendarDates
import com.joon.ringout.domain.missionhistory.plusDays
import com.joon.ringout.domain.missionhistory.weekDates
import com.joon.ringout.domain.missionhistory.yearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class RecordsViewModel(
    private val getRecordsHistory: GetRecordsHistory,
    initialDate: MissionDate,
    private val activityRepository: AlarmActivityRepository? = null,
    private val currentDate: () -> MissionDate = { initialDate },
    coroutineScope: CoroutineScope? = null,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val monthLoader: AlarmOccurrenceMonthLoader? = null,
    private val currentCacheMonth: () -> MissionYearMonth = { currentAlarmOccurrenceMonth() },
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
    private val weekCacheJobs = mutableMapOf<MissionYearMonth, Job>()
    private var calendarCacheJob: Job? = null
    private var failedWeekMonths = emptySet<MissionYearMonth>()
    private var weekHistory: List<AlarmUsageRecord> = emptyList()
    private var requestedDate: MissionDate = initialDate
    private var weekChangeRetryDate: MissionDate? = null

    fun selectDate(date: MissionDate) {
        val previous = uiState.value
        closeCalendar()
        val today = currentDate()
        if (today != previous.today) {
            mutableUiState.update { it.copy(today = today) }
            changeWeek(date)
            return
        }
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
                activitySummary = weekSummaries[date] ?: AlarmActivitySummary(),
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
        calendarCacheJob?.cancel()
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
        if (uiState.value.errorMessage == null && uiState.value.summaryErrorMessage == null && failedWeekMonths.isNotEmpty()) {
            loadWeekCache(failedWeekMonths, retryOnly = true)
            return
        }
        observeWeekSummaries(forceReload = true)
        loadWeek()
        loadWeekCache(uiState.value.selectedDate.weekDates().cacheMonths())
    }

    fun retryCalendar() {
        if (!uiState.value.isCalendarVisible) return
        if (uiState.value.calendarErrorMessage == null && uiState.value.calendarCacheErrorMessage != null) {
            loadCalendarCache(uiState.value.calendarMonth)
        } else {
            loadCalendar()
        }
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
        loadWeekCache(uiState.value.selectedDate.weekDates().cacheMonths())
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
        loadWeekCache(dates.cacheMonths())
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
                observeWeekSnapshot(dates, uiState.value.today).collectLatest { (history, summaries) ->
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
                                recordsDayUiState(day, state.today, selectedDate, historyByDate[day].orEmpty(), summaries[day])
                            },
                            activitySummary = summaries[selectedDate] ?: AlarmActivitySummary(),
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
        val activityRepository = activityRepository ?: return
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
        val hasDisplayedWeek = !state.isLoading && state.errorMessage == null &&
            state.weekDays.map(RecordsDayUiState::date) == dates
        if (!hasDisplayedWeek) weekHistory = emptyList()
        mutableUiState.update {
            it.copy(
                isLoading = !hasDisplayedWeek,
                errorMessage = null,
            )
        }
        weekLoadJob = scope.launch {
            try {
                val snapshots = if (activityRepository == null) {
                    getRecordsHistory.observeSnapshot(dates, state.today)
                } else {
                    getRecordsHistory.observe(dates, state.today).map { RecordsHistorySnapshot(it) }
                }
                snapshots.collect { (history, summaries) ->
                    if (requestId != weekRequestId) return@collect
                    weekHistory = history
                    if (activityRepository == null) weekSummaries = summaries
                    showWeekHistory(history)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (requestId != weekRequestId) return@launch
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        isSummaryLoading = activityRepository != null && it.isSummaryLoading,
                        errorMessage = RecordsLoadErrorMessage,
                    )
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
                activitySummary = if (activityRepository == null) {
                    weekSummaries[state.selectedDate] ?: AlarmActivitySummary()
                } else {
                    state.activitySummary
                },
                isSummaryLoading = activityRepository != null && state.isSummaryLoading,
                weekDays = state.selectedDate.weekDates().map { date ->
                    recordsDayUiState(date, state.today, state.selectedDate, historyByDate[date].orEmpty(), weekSummaries[date])
                },
            )
        }
    }

    private fun loadCalendar() {
        val requestId = ++calendarRequestId
        calendarLoadJob?.cancel()
        val state = uiState.value
        val dates = state.calendarMonth.calendarDates()
        loadCalendarCache(state.calendarMonth)
        val hasDisplayedMonth = state.calendarDays.map { it?.date } == dates &&
            state.calendarDays.any { day -> day != null && (day.recordCount != 0 || day.ringingCount != null) }
        mutableUiState.update {
            it.copy(
                isCalendarLoading = !hasDisplayedMonth,
                calendarErrorMessage = null,
                calendarDays = if (hasDisplayedMonth) it.calendarDays else dates.map { date ->
                    date?.let { recordsDayUiState(it, state.today, state.selectedDate) }
                },
            )
        }
        calendarLoadJob = scope.launch {
            try {
                getRecordsHistory.observeSnapshot(dates.filterNotNull(), state.today).collect { (history, summaries) ->
                    if (requestId != calendarRequestId) return@collect
                    val historyByDate = history.groupBy(AlarmUsageRecord::date)
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
                                        summary = summaries[it],
                                    )
                                }
                            },
                        )
                    }
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

    private fun observeWeekSnapshot(dates: List<MissionDate>, today: MissionDate) =
        if (activityRepository == null) {
            getRecordsHistory.observeSnapshot(dates, today)
        } else {
            combine(getRecordsHistory.observe(dates, today), activityRepository.observeSummaries(dates)) { history, summaries ->
                RecordsHistorySnapshot(history, summaries)
            }
        }

    private fun List<MissionDate>.cacheMonths() = map(MissionDate::yearMonth).toSet()

    private fun MissionYearMonth.isFutureCacheMonth(): Boolean {
        val current = currentCacheMonth()
        return year > current.year || (year == current.year && month > current.month)
    }

    private fun loadWeekCache(months: Set<MissionYearMonth>, retryOnly: Boolean = false) {
        val loader = monthLoader ?: return
        if (!retryOnly) {
            weekCacheJobs.values.forEach { it.cancel() }
            weekCacheJobs.clear()
        }
        failedWeekMonths = emptySet()
        mutableUiState.update { it.copy(weekCacheErrorMessage = null) }
        months.filterNot { it.isFutureCacheMonth() }.forEach { month ->
            weekCacheJobs[month] = scope.launch {
                val loaded = loader.ensure(month)
                ensureActive()
                if (!loaded) {
                    failedWeekMonths = failedWeekMonths + month
                    mutableUiState.update { it.copy(weekCacheErrorMessage = RecordsCacheErrorMessage) }
                }
            }
        }
    }

    private fun loadCalendarCache(month: MissionYearMonth) {
        val loader = monthLoader ?: return
        calendarCacheJob?.cancel()
        mutableUiState.update { it.copy(calendarCacheErrorMessage = null) }
        if (month.isFutureCacheMonth()) return
        calendarCacheJob = scope.launch {
            val loaded = loader.ensure(month)
            ensureActive()
            if (!loaded) mutableUiState.update { it.copy(calendarCacheErrorMessage = RecordsCacheErrorMessage) }
        }
    }
}

internal const val RecordsLoadErrorMessage = "저장된 알람 사용 기록을 불러오지 못했어요."
internal const val RecordsCacheErrorMessage = "서버에서 알람 사용 기록을 가져오지 못했어요."
private const val SummaryLoadingIndicatorDelayMillis = 500L
private const val WeekLoadingMinimumEndMillis = 750L
