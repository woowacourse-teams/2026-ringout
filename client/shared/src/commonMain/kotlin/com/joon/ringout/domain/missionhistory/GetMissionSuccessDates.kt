package com.joon.ringout.domain.missionhistory

class GetMissionSuccessDates(
    private val repository: MissionHistoryRepository,
) {
    suspend operator fun invoke(month: MissionYearMonth): Set<MissionDate> =
        repository.getHistory(month)
            .asSequence()
            .filter { it.result == MissionResult.SUCCESS }
            .map(MissionHistoryEntry::completedAt)
            .filter { it.belongsTo(month) }
            .toSet()
}
