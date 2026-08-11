package com.joon.ringout.domain.missionhistory

class RecordMissionResult(
    private val repository: MissionHistoryRepository,
) {
    suspend operator fun invoke(
        result: MissionResult,
        completedAt: MissionDate,
        occurrenceId: String,
    ): Boolean {
        require(occurrenceId.isNotBlank()) { "Mission occurrence ID must not be blank." }
        return repository.record(
            MissionHistoryEntry(
                result = result,
                completedAt = completedAt,
                occurrenceId = occurrenceId,
            ),
        )
    }
}
