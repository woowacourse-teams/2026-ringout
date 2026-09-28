package com.joon.ringout.domain.missionhistory

/** A ringing occurrence can exist before any mission result has been recorded. */
data class AlarmUsageRecord(
    val key: String,
    val date: MissionDate,
    val occurrenceId: String? = null,
    val result: MissionResult? = null,
    val ringingStartedAtEpochMillis: Long? = null,
    val ringingStoppedAtEpochMillis: Long? = null,
    val missionCompletedAtEpochMillis: Long? = null,
    val isRingingStartObserved: Boolean = false,
    val alarmId: String? = null,
)

/** Fallback for history sources without separate ringing events. */
fun MissionHistoryEntry.toAlarmUsageRecord(legacyIndex: Int = 0): AlarmUsageRecord = AlarmUsageRecord(
    key = occurrenceId?.let { "occurrence:$it" } ?: "history:${completedAt.iso8601}:$legacyIndex",
    date = completedAt,
    occurrenceId = occurrenceId,
    result = result,
    ringingStartedAtEpochMillis = ringingStartedAtEpochMillis,
    ringingStoppedAtEpochMillis = ringingStoppedAtEpochMillis,
    missionCompletedAtEpochMillis = missionCompletedAtEpochMillis,
    isRingingStartObserved = isRingingStartObserved,
)
