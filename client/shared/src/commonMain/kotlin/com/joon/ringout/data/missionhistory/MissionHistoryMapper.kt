package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionResult

internal fun MissionHistoryDto.toDomain(): MissionHistoryEntry = MissionHistoryEntry(
    result = when (result) {
        "SUCCESS" -> MissionResult.SUCCESS
        "FAILURE" -> MissionResult.FAILURE
        else -> error("Unsupported mission result: $result")
    },
    completedAt = MissionDate.parse(completedAt),
)
