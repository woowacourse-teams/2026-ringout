package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface MissionHistoryDataSource {
    suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto>

    fun observeHistory(month: MissionYearMonth): Flow<List<MissionHistoryDto>> = flow { emit(getHistory(month)) }

    /** Returns true only when the occurrence was newly persisted. */
    suspend fun record(history: MissionHistoryDto): Boolean
}
