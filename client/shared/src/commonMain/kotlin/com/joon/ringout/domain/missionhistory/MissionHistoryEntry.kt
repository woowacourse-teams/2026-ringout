package com.joon.ringout.domain.missionhistory

enum class MissionResult {
    SUCCESS,
    FAILURE,
}

data class MissionHistoryEntry(
    val result: MissionResult,
    val completedAt: MissionDate,
)
