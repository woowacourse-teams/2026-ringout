package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionResult

internal fun MissionHistoryDto.toDomain(): MissionHistoryEntry = MissionHistoryEntry(
    result = MissionResult.fromPersistedValue(result)
        ?: error("Unsupported mission result: $result"),
    completedAt = MissionDate.parse(completedAt),
    occurrenceId = occurrenceId,
)

internal fun MissionHistoryEntry.toDto(): MissionHistoryDto = MissionHistoryDto(
    result = result.persistedValue,
    completedAt = completedAt.iso8601,
    occurrenceId = occurrenceId,
)

internal fun MissionHistoryEntity.toDto(): MissionHistoryDto = MissionHistoryDto(
    result = result,
    completedAt = completedAt,
    occurrenceId = occurrenceId,
)

internal fun MissionHistoryDto.toEntity(): MissionHistoryEntity = MissionHistoryEntity(
    result = result,
    completedAt = completedAt,
    occurrenceId = occurrenceId,
)
