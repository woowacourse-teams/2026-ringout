package com.joon.ringout.domain.missionhistory

/** Resolves stable record groups for the records and arrival sharing flows. */
class GetAlarmRecordGroup(private val repository: MissionHistoryRepository) {
    suspend fun byDateAndKey(date: MissionDate, groupKey: String): AlarmUsageRecordGroup? =
        repository.getLocalRecords(date.yearMonth)
            .filter { it.date == date }
            .groupByAlarm()
            .firstOrNull { it.key == groupKey }

    suspend fun byOccurrenceId(occurrenceId: String): AlarmUsageRecordGroup? {
        if (occurrenceId.isBlank()) return null
        return repository.getLocalRecordsOnOccurrenceDate(occurrenceId)
            .groupByAlarm()
            .firstOrNull { group -> group.entries.any { it.occurrenceId == occurrenceId } }
    }
}
