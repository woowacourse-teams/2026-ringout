package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.missionhistory.MissionYearMonth

class DefaultMissionHistoryRepository(
    private val dataSource: MissionHistoryDataSource,
) : MissionHistoryRepository {
    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> =
        dataSource.getHistory(month).map(MissionHistoryDto::toDomain)

    override suspend fun record(entry: MissionHistoryEntry): Boolean {
        require(!entry.occurrenceId.isNullOrBlank()) {
            "Mission occurrence ID must not be blank."
        }
        return dataSource.record(entry.toDto())
    }
}
