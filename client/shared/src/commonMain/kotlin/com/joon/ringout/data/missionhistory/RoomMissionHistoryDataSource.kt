package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth

class RoomMissionHistoryDataSource(
    private val missionHistoryDao: MissionHistoryDao,
) : MissionHistoryDataSource {
    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto> {
        val firstDay = MissionDate.of(month.year, month.month, 1).iso8601
        val lastDay = MissionDate.of(month.year, month.month, month.dayCount).iso8601
        return missionHistoryDao.getHistory(firstDay, lastDay).map(MissionHistoryEntity::toDto)
    }

    override suspend fun record(history: MissionHistoryDto): Boolean {
        require(!history.occurrenceId.isNullOrBlank()) {
            "Mission occurrence ID must not be blank."
        }
        return missionHistoryDao.insert(history.toEntity())
    }
}
