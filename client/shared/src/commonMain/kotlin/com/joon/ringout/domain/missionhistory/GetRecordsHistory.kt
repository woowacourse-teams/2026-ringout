package com.joon.ringout.domain.missionhistory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Reads local ringing occurrences and legacy mission results in a date range. */
class GetRecordsHistory(
    private val repository: MissionHistoryRepository,
) {
    fun observe(dates: List<MissionDate>, today: MissionDate): Flow<List<AlarmUsageRecord>> {
        val visibleDates = dates.filterNot { it.isAfter(today) }.toSet()
        val months = visibleDates.map(MissionDate::yearMonth).distinct()
        if (months.isEmpty()) return flowOf(emptyList())
        return combine(months.map { month ->
            repository.observeLocalRecords(month).map { entries ->
                entries.filter { it.date.belongsTo(month) && it.date in visibleDates }
            }
        }) { histories -> histories.flatMap { it }.sortedBy { it.date.iso8601 } }
    }

    suspend operator fun invoke(
        dates: List<MissionDate>,
        today: MissionDate,
    ): List<AlarmUsageRecord> {
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
