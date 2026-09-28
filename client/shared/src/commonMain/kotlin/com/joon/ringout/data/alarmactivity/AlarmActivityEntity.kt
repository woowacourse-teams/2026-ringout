package com.joon.ringout.data.alarmactivity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

// Deliberately has no foreign key: deleting an alarm must preserve its history.
@Entity(tableName = "alarm_activity_events", indices = [Index(value = ["local_date", "type"])])
data class AlarmActivityEntity(
    @PrimaryKey @ColumnInfo(name = "event_key") val eventKey: String,
    @ColumnInfo(name = "alarm_id") val alarmId: String,
    val type: String,
    @ColumnInfo(name = "occurred_at_epoch_millis") val occurredAtEpochMillis: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "schedule_version", defaultValue = "1") val scheduleVersion: Long = 1,
) {
    companion object {
        fun rang(alarmId: String, occurrenceId: String, timestamp: AlarmActivityTimestamp, scheduleVersion: Long = 1) = AlarmActivityEntity(
            "rang:$occurrenceId", alarmId, "RANG", timestamp.epochMillis, timestamp.localDate, scheduleVersion,
        )

        /** The stop confirms an occurrence, but its timestamp must never stand in for the start. */
        fun rangConfirmedByStop(alarmId: String, occurrenceId: String, timestamp: AlarmActivityTimestamp, scheduleVersion: Long = 1) = AlarmActivityEntity(
            "rang:$occurrenceId", alarmId, "RANG_CONFIRMED_BY_STOP", timestamp.epochMillis, timestamp.localDate, scheduleVersion,
        )
    }
}

@Entity(tableName = "alarm_activity_tracking")
data class AlarmActivityTrackingEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "started_at_epoch_millis") val startedAtEpochMillis: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
)

/** Persists the currently observed AlarmKit session across app restarts. */
@Entity(tableName = "alarm_ringing_observations")
data class AlarmRingingObservationEntity(
    @PrimaryKey @ColumnInfo(name = "system_alarm_id") val systemAlarmId: String,
    @ColumnInfo(name = "event_key") val eventKey: String,
)

data class AlarmActivityTimestamp(val epochMillis: Long, val localDate: String)

internal expect fun currentAlarmActivityTimestamp(): AlarmActivityTimestamp
