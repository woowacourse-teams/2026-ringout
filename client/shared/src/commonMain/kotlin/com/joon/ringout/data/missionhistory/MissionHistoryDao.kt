package com.joon.ringout.data.missionhistory

import kotlinx.coroutines.flow.Flow
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Transaction
import com.joon.ringout.data.alarmactivity.AlarmOccurrenceTimesAccess
import com.joon.ringout.data.alarmactivity.AlarmOccurrenceTimesEntity
import androidx.room3.Query

@Dao
interface MissionHistoryDao : AlarmOccurrenceTimesAccess {
    @Query(ALARM_USAGE_RECORDS_QUERY)
    suspend fun getRecords(startInclusive: String, endInclusive: String): List<AlarmUsageRecordRow>

    @Query(ALARM_USAGE_RECORDS_QUERY)
    fun observeRecords(startInclusive: String, endInclusive: String): Flow<List<AlarmUsageRecordRow>>

    @Query("""
        SELECT h.*, t.ringing_started_at, t.ringing_stopped_at,
            t.mission_completed_at, t.ringing_start_observed
        FROM mission_history h
        LEFT JOIN alarm_occurrence_times t ON t.occurrence_id = h.occurrence_id
        WHERE h.completed_at BETWEEN :startInclusive AND :endInclusive
        ORDER BY h.completed_at ASC, h.id ASC
    """)
    suspend fun getHistoryWithTimes(startInclusive: String, endInclusive: String): List<MissionHistoryWithTimes>

    @Query("""
        SELECT h.*, t.ringing_started_at, t.ringing_stopped_at,
            t.mission_completed_at, t.ringing_start_observed
        FROM mission_history h
        LEFT JOIN alarm_occurrence_times t ON t.occurrence_id = h.occurrence_id
        WHERE h.completed_at BETWEEN :startInclusive AND :endInclusive
        ORDER BY h.completed_at ASC, h.id ASC
    """)
    fun observeHistoryWithTimes(startInclusive: String, endInclusive: String): Flow<List<MissionHistoryWithTimes>>

    @Transaction
    suspend fun insertWithTimes(history: MissionHistoryEntity, times: AlarmOccurrenceTimesEntity): Boolean {
        val inserted = insert(history)
        mergeOccurrenceTimes(times)
        return inserted
    }

    @Query(
        """
        SELECT * FROM mission_history
        WHERE completed_at BETWEEN :startInclusive AND :endInclusive
        ORDER BY completed_at ASC, id ASC
        """,
    )
    suspend fun getHistory(
        startInclusive: String,
        endInclusive: String,
    ): List<MissionHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicate(history: MissionHistoryEntity): Long

    /**
     * Inserts a mission result exactly once for its occurrence ID.
     *
     * SQLite allows multiple `NULL` values in the unique occurrence index so legacy rows remain
     * readable, while newly recorded rows use a non-null occurrence ID.
     */
    suspend fun insert(history: MissionHistoryEntity): Boolean =
        insertIgnoringDuplicate(history) != -1L
}

// Ringing determines the card's date. A later result enriches that same occurrence;
// legacy results without a ringing event remain visible on their completion date.
private const val ALARM_USAGE_RECORDS_QUERY = """
    SELECT record_key, record_date, alarm_id, occurrence_id, result, ringing_started_at,
        ringing_stopped_at, mission_completed_at, ringing_start_observed
    FROM (
        SELECT 'occurrence:' || SUBSTR(a.event_key, 6) AS record_key,
            a.local_date AS record_date, a.alarm_id, SUBSTR(a.event_key, 6) AS occurrence_id,
            h.result, COALESCE(t.ringing_started_at, a.occurred_at_epoch_millis) AS ringing_started_at,
            t.ringing_stopped_at, t.mission_completed_at, t.ringing_start_observed,
            a.occurred_at_epoch_millis AS sort_time, 0 AS legacy_id
        FROM alarm_activity_events a
        LEFT JOIN alarm_occurrence_times t ON t.occurrence_id = SUBSTR(a.event_key, 6)
        LEFT JOIN mission_history h ON h.occurrence_id = SUBSTR(a.event_key, 6)
        WHERE a.type = 'RANG' AND a.local_date BETWEEN :startInclusive AND :endInclusive
        UNION ALL
        SELECT COALESCE('occurrence:' || h.occurrence_id, 'history:' || h.id) AS record_key,
            h.completed_at AS record_date, NULL AS alarm_id, h.occurrence_id, h.result,
            t.ringing_started_at, t.ringing_stopped_at, t.mission_completed_at, t.ringing_start_observed,
            COALESCE(t.ringing_started_at, t.mission_completed_at, 0) AS sort_time, h.id AS legacy_id
        FROM mission_history h
        LEFT JOIN alarm_occurrence_times t ON t.occurrence_id = h.occurrence_id
        WHERE h.completed_at BETWEEN :startInclusive AND :endInclusive
            AND NOT EXISTS (
                SELECT 1 FROM alarm_activity_events a
                WHERE a.type = 'RANG' AND a.event_key = 'rang:' || h.occurrence_id
            )
    )
    ORDER BY record_date ASC, sort_time ASC, legacy_id ASC, record_key ASC
"""
