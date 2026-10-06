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
    val ringingStartedAtEpochMillis: Long? = null,
    val ringingStoppedAtEpochMillis: Long? = null,
    val missionCompletedAtEpochMillis: Long? = null,
    val isRingingStartObserved: Boolean = false,
)
