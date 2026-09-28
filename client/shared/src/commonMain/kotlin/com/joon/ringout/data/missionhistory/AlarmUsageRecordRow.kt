package com.joon.ringout.data.missionhistory

import androidx.room3.ColumnInfo
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult

data class AlarmUsageRecordRow(
    @ColumnInfo(name = "record_key") val key: String,
    @ColumnInfo(name = "record_date") val date: String,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String?,
    val result: String?,
    @ColumnInfo(name = "ringing_started_at") val ringingStartedAtEpochMillis: Long?,
    @ColumnInfo(name = "ringing_stopped_at") val ringingStoppedAtEpochMillis: Long?,
    @ColumnInfo(name = "mission_completed_at") val missionCompletedAtEpochMillis: Long?,
    @ColumnInfo(name = "ringing_start_observed") val isRingingStartObserved: Boolean?,
    @ColumnInfo(name = "alarm_id") val alarmId: String?,
    @ColumnInfo(name = "ringing_scheduled_at") val ringingScheduledAtEpochMillis: Long?,
)

internal fun AlarmUsageRecordRow.toDomain() = AlarmUsageRecord(
    key = key,
    date = MissionDate.parse(date),
    occurrenceId = occurrenceId,
    result = result?.let { MissionResult.fromPersistedValue(it) ?: error("Unsupported mission result: $it") },
    ringingStartedAtEpochMillis = ringingStartedAtEpochMillis,
    ringingStoppedAtEpochMillis = ringingStoppedAtEpochMillis,
    missionCompletedAtEpochMillis = missionCompletedAtEpochMillis,
    isRingingStartObserved = isRingingStartObserved == true,
    alarmId = alarmId,
    ringingScheduledAtEpochMillis = ringingScheduledAtEpochMillis,
)
