package com.joon.ringout.data.alarmactivity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction

/** Kept independently of alarm settings and active mission state. */
@Entity(tableName = "alarm_occurrence_times")
data class AlarmOccurrenceTimesEntity(
    @PrimaryKey @ColumnInfo(name = "occurrence_id") val occurrenceId: String,
    @ColumnInfo(name = "ringing_started_at") val ringingStartedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "ringing_stopped_at") val ringingStoppedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "mission_completed_at") val missionCompletedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "ringing_start_observed") val isRingingStartObserved: Boolean = false,
)

/** Shared by the activity and mission DAOs so result + completion time commit atomically. */
interface AlarmOccurrenceTimesAccess {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrenceTimes(times: AlarmOccurrenceTimesEntity)

    @Query("""
        UPDATE alarm_occurrence_times SET
            ringing_start_observed = CASE WHEN ringing_started_at IS NULL AND :startedAt IS NOT NULL THEN :isObserved ELSE ringing_start_observed END,
            ringing_started_at = COALESCE(ringing_started_at, :startedAt),
            ringing_stopped_at = COALESCE(ringing_stopped_at, :stoppedAt),
            mission_completed_at = COALESCE(mission_completed_at, :completedAt)
        WHERE occurrence_id = :occurrenceId
    """)
    suspend fun fillMissingOccurrenceTimes(
        occurrenceId: String,
        startedAt: Long?,
        stoppedAt: Long?,
        completedAt: Long?,
        isObserved: Boolean,
    )

    @Query("SELECT * FROM alarm_occurrence_times WHERE occurrence_id = :occurrenceId")
    suspend fun getOccurrenceTimes(occurrenceId: String): AlarmOccurrenceTimesEntity?

    @Transaction
    suspend fun mergeOccurrenceTimes(times: AlarmOccurrenceTimesEntity) {
        require(times.occurrenceId.isNotBlank())
        insertOccurrenceTimes(times)
        fillMissingOccurrenceTimes(
            times.occurrenceId,
            times.ringingStartedAtEpochMillis,
            times.ringingStoppedAtEpochMillis,
            times.missionCompletedAtEpochMillis,
            times.isRingingStartObserved,
        )
    }
}
