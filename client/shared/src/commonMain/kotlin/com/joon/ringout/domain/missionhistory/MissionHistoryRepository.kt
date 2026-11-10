package com.joon.ringout.domain.missionhistory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

interface MissionHistoryRepository {
    suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry>

    /** Reads device history regardless of the current authentication state. */
    suspend fun getLocalHistory(month: MissionYearMonth): List<MissionHistoryEntry> = getHistory(month)

    fun observeLocalHistory(month: MissionYearMonth): Flow<List<MissionHistoryEntry>> = flow { emit(getLocalHistory(month)) }

    suspend fun getLocalRecords(month: MissionYearMonth): List<AlarmUsageRecord> =
        getLocalHistory(month).mapIndexed { index, entry -> entry.toAlarmUsageRecord(index) }

    fun observeLocalRecords(month: MissionYearMonth): Flow<List<AlarmUsageRecord>> =
        observeLocalHistory(month).map { history -> history.mapIndexed { index, entry -> entry.toAlarmUsageRecord(index) } }

    /** Reads local records on the occurrence's ringing date, or its completion date if no ringing exists. */
    suspend fun getLocalRecordsOnOccurrenceDate(occurrenceId: String): List<AlarmUsageRecord> = emptyList()

    /** Returns true when this occurrence was recorded for the first time. */
    suspend fun record(entry: MissionHistoryEntry): Boolean
}
