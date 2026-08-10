package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth

class InMemoryMissionHistoryDataSource(
    private val entries: List<MissionHistoryDto>,
) : MissionHistoryDataSource {
    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto> =
        entries.filter { dto ->
            runCatching { MissionDate.parse(dto.completedAt).belongsTo(month) }
                .getOrDefault(true)
        }
}
