package com.joon.ringout.domain.missionhistory

class GetMissionResultsByDate(
    private val repository: MissionHistoryRepository,
) {
    /**
     * Returns the latest result for each date in [month]. History is ordered by DAO ID ascending,
     * so assigning in iteration order deliberately makes the highest-ID result win on each date.
     */
    suspend operator fun invoke(month: MissionYearMonth): Map<MissionDate, MissionResult> =
        buildMap {
            repository.getHistory(month)
                .asSequence()
                .filter { it.completedAt.belongsTo(month) }
                .forEach { entry -> put(entry.completedAt, entry.result) }
        }
}
