package com.joon.ringout.domain.missionhistory

interface MissionHistoryRepository {
    suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry>

    /** Reads device history regardless of the current authentication state. */
    suspend fun getLocalHistory(month: MissionYearMonth): List<MissionHistoryEntry> = getHistory(month)

    /** Returns true when this occurrence was recorded for the first time. */
    suspend fun record(entry: MissionHistoryEntry): Boolean
}
