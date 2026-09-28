package com.joon.ringout.domain.missionhistory

/** Reads every local result in a date range, including legacy entries without an occurrence ID. */
class GetRecordsHistory(
    private val repository: MissionHistoryRepository,
) {
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
