package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth

class InMemoryMissionHistoryDataSource(
    entries: List<MissionHistoryDto>,
) : MissionHistoryDataSource {
    private val entries = entries.toMutableList()

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto> =
        entries.filter { dto ->
            runCatching { MissionDate.parse(dto.completedAt).belongsTo(month) }
                .getOrDefault(true)
        }

    override suspend fun record(history: MissionHistoryDto): Boolean {
        require(!history.occurrenceId.isNullOrBlank()) {
            "Mission occurrence ID must not be blank."
        }
        if (entries.any { it.occurrenceId == history.occurrenceId }) return false
        entries += history
        return true
    }
}
