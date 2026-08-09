package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.missionhistory.MissionYearMonth

class DefaultMissionHistoryRepository(
    private val dataSource: MissionHistoryDataSource,
) : MissionHistoryRepository {
    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> =
        dataSource.getHistory(month).map(MissionHistoryDto::toDomain)
}
