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
