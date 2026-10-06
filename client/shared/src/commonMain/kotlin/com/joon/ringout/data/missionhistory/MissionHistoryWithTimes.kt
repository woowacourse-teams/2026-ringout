package com.joon.ringout.data.missionhistory

import androidx.room3.ColumnInfo
import androidx.room3.Embedded

data class MissionHistoryWithTimes(
    @Embedded val history: MissionHistoryEntity,
    @ColumnInfo(name = "ringing_started_at") val ringingStartedAtEpochMillis: Long?,
    @ColumnInfo(name = "ringing_stopped_at") val ringingStoppedAtEpochMillis: Long?,
    @ColumnInfo(name = "mission_completed_at") val missionCompletedAtEpochMillis: Long?,
    @ColumnInfo(name = "ringing_start_observed") val isRingingStartObserved: Boolean?,
)

internal fun MissionHistoryWithTimes.toDto() = MissionHistoryDto(
    result = history.result,
    completedAt = history.completedAt,
    occurrenceId = history.occurrenceId,
    ringingStartedAtEpochMillis = ringingStartedAtEpochMillis,
    ringingStoppedAtEpochMillis = ringingStoppedAtEpochMillis,
    missionCompletedAtEpochMillis = missionCompletedAtEpochMillis,
    isRingingStartObserved = isRingingStartObserved == true,
)
