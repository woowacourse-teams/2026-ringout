package com.joon.ringout.domain.missionhistory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Reads every local result in a date range, including legacy entries without an occurrence ID. */
class GetRecordsHistory(
    private val repository: MissionHistoryRepository,
) {
    fun observe(dates: List<MissionDate>, today: MissionDate): Flow<List<MissionHistoryEntry>> {
        val visibleDates = dates.filterNot { it.isAfter(today) }.toSet()
        val months = visibleDates.map(MissionDate::yearMonth).distinct()
        if (months.isEmpty()) return flowOf(emptyList())
        return combine(months.map { month ->
            repository.observeLocalHistory(month).map { entries ->
                entries.filter { it.completedAt.belongsTo(month) && it.completedAt in visibleDates }
            }
        }) { histories -> histories.flatMap { it }.sortedBy { it.completedAt.iso8601 } }
    }

    suspend operator fun invoke(
        dates: List<MissionDate>,
        today: MissionDate,
    ): List<MissionHistoryEntry> {
        val visibleDates = dates.filterNot { it.isAfter(today) }.toSet()
        return visibleDates.map(MissionDate::yearMonth)
            .distinct()
            .flatMap { month ->
                repository.getLocalHistory(month).filter { entry ->
                    entry.completedAt.belongsTo(month) && entry.completedAt in visibleDates
                }
            }
            // Stable sorting preserves DAO insertion order for multiple results on the same day.
            .sortedBy { it.completedAt.iso8601 }
    }
}
