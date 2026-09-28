package com.joon.ringout.presentation.records

import com.joon.ringout.domain.alarmactivity.AlarmActivitySummary
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.AlarmUsageRecordGroup
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.calendarDates
import com.joon.ringout.domain.missionhistory.isAfter
import com.joon.ringout.domain.missionhistory.weekDates
import com.joon.ringout.domain.missionhistory.yearMonth

data class RecordsDayUiState(
    val date: MissionDate,
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val isFuture: Boolean = false,
    val result: MissionResult? = null,
    val recordCount: Int = 0,
)

data class RecordsUiState(
    val today: MissionDate = MissionDate.of(2026, 9, 28),
    val selectedDate: MissionDate = today,
    val weekDays: List<RecordsDayUiState> = selectedDate.weekDates().map {
        recordsDayUiState(it, today, selectedDate)
    },
    val records: List<AlarmUsageRecordGroup> = emptyList(),
    val activitySummary: AlarmActivitySummary = AlarmActivitySummary(),
    val isSummaryLoading: Boolean = false,
    val summaryErrorMessage: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val calendarMonth: MissionYearMonth = selectedDate.yearMonth,
    val calendarDays: List<RecordsDayUiState?> = calendarMonth.calendarDates().map { date ->
        date?.let { recordsDayUiState(it, today, selectedDate) }
    },
    val isCalendarVisible: Boolean = false,
    val isCalendarLoading: Boolean = false,
    val calendarErrorMessage: String? = null,
)

internal fun recordsDayUiState(
    date: MissionDate,
    today: MissionDate,
    selectedDate: MissionDate,
    records: List<AlarmUsageRecord> = emptyList(),
): RecordsDayUiState = RecordsDayUiState(
    date = date,
    isToday = date == today,
    isSelected = date == selectedDate,
    isFuture = date.isAfter(today),
    result = records.lastOrNull { it.result != null }?.result,
    recordCount = records.size,
)
