package com.joon.ringout.domain.missionhistory

interface MissionHistoryRepository {
    suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry>

    /** Returns true when this occurrence was recorded for the first time. */
    suspend fun record(entry: MissionHistoryEntry): Boolean
}
