package com.joon.ringout.domain.missionhistory

enum class MissionResult(
    val persistedValue: String,
) {
    SUCCESS("SUCCESS"),
    FAILURE("FAILURE"),
    ;

    companion object {
        fun fromPersistedValue(value: String): MissionResult? =
            entries.firstOrNull { result -> result.persistedValue == value }
    }
}

data class MissionHistoryEntry(
    val result: MissionResult,
    val completedAt: MissionDate,
    val occurrenceId: String? = null,
)
