package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.yearMonth

class InMemoryMissionHistoryDataSource(
    entries: List<MissionHistoryDto>,
) : MissionHistoryDataSource {
    private val entries = entries.toMutableList()

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto> =
        entries.filter { dto ->
            runCatching { MissionDate.parse(dto.completedAt).belongsTo(month) }
                .getOrDefault(true)
        }

    override suspend fun getRecordsOnOccurrenceDate(occurrenceId: String): List<AlarmUsageRecord> {
        if (occurrenceId.isBlank()) return emptyList()
        val entry = entries.firstOrNull { it.occurrenceId == occurrenceId } ?: return emptyList()
        val date = MissionDate.parse(entry.completedAt)
        return getRecords(date.yearMonth).filter { it.date == date }
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
