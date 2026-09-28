package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.toAlarmUsageRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

interface MissionHistoryDataSource {
    suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto>

    fun observeHistory(month: MissionYearMonth): Flow<List<MissionHistoryDto>> = flow { emit(getHistory(month)) }

    suspend fun getRecords(month: MissionYearMonth): List<AlarmUsageRecord> =
        getHistory(month).mapIndexed { index, entry -> entry.toDomain().toAlarmUsageRecord(index) }

    fun observeRecords(month: MissionYearMonth): Flow<List<AlarmUsageRecord>> =
        observeHistory(month).map { history -> history.mapIndexed { index, entry -> entry.toDomain().toAlarmUsageRecord(index) } }

    /** Returns true only when the occurrence was newly persisted. */
    suspend fun record(history: MissionHistoryDto): Boolean
}
