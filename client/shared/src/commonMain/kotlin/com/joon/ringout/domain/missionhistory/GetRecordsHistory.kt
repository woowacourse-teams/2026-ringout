package com.joon.ringout.domain.missionhistory

import com.joon.ringout.domain.alarmactivity.AlarmActivitySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class RecordsHistorySnapshot(
    val records: List<AlarmUsageRecord>,
    val summaries: Map<MissionDate, AlarmActivitySummary> = emptyMap(),
)

interface RecordsHistoryRepository {
    fun observe(dates: List<MissionDate>): Flow<RecordsHistorySnapshot>
}

class GetRecordsHistory(
    private val repository: MissionHistoryRepository,
    private val recordsRepository: RecordsHistoryRepository? = null,
) {
    fun observeSnapshot(dates: List<MissionDate>, today: MissionDate): Flow<RecordsHistorySnapshot> {
        val visibleDates = dates.filterNot { it.isAfter(today) }.toSet()
        if (visibleDates.isEmpty()) return flowOf(RecordsHistorySnapshot(emptyList()))
        recordsRepository?.let { return it.observe(visibleDates.toList()) }
        val months = visibleDates.map(MissionDate::yearMonth).distinct()
        return combine(months.map { month ->
            repository.observeLocalRecords(month).map { entries ->
                entries.filter { it.date.belongsTo(month) && it.date in visibleDates }
            }
        }) { histories -> RecordsHistorySnapshot(histories.flatMap { it }.sortedBy { it.date.iso8601 }) }
    }

    fun observe(dates: List<MissionDate>, today: MissionDate): Flow<List<AlarmUsageRecord>> =
        observeSnapshot(dates, today).map { it.records }

    suspend operator fun invoke(
        dates: List<MissionDate>,
        today: MissionDate,
    ): List<AlarmUsageRecord> {
        if (recordsRepository != null) return observe(dates, today).first()
        val visibleDates = dates.filterNot { it.isAfter(today) }.toSet()
        return visibleDates.map(MissionDate::yearMonth)
            .distinct()
            .flatMap { month ->
                repository.getLocalRecords(month).filter { entry ->
                    entry.date.belongsTo(month) && entry.date in visibleDates
                }
            }
            // Stable sorting preserves DAO order for multiple occurrences on the same day.
            .sortedBy { it.date.iso8601 }
    }
}
