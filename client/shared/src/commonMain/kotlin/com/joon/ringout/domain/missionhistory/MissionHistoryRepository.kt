package com.joon.ringout.domain.missionhistory

interface MissionHistoryRepository {
    suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry>
}
