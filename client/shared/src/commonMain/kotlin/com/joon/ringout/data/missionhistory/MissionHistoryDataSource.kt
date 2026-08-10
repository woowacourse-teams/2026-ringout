package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionYearMonth

interface MissionHistoryDataSource {
    suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto>

    /** Returns true only when the occurrence was newly persisted. */
    suspend fun record(history: MissionHistoryDto): Boolean
}
