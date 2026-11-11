package com.joon.ringout.data.missionhistory

import kotlinx.coroutines.flow.Flow
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Transaction
import com.joon.ringout.data.alarmactivity.AlarmOccurrenceTimesAccess
import com.joon.ringout.data.alarmactivity.AlarmOccurrenceTimesEntity
import androidx.room3.Query
import com.joon.ringout.data.arrival.ArrivalGrowthActivationEntity
import com.joon.ringout.data.arrival.ArrivalResultEntity
import com.joon.ringout.data.arrival.ArrivalResultWriteAccess
import com.joon.ringout.data.forest.ForestWriteAccess
import com.joon.ringout.domain.missionhistory.MissionResult
import kotlin.time.Clock

@Dao
interface MissionHistoryDao : AlarmOccurrenceTimesAccess, ArrivalResultWriteAccess, ForestWriteAccess {
    @Query(ALARM_USAGE_RECORDS_QUERY)
    suspend fun getRecords(startInclusive: String, endInclusive: String): List<AlarmUsageRecordRow>

    @Query(ALARM_USAGE_RECORDS_QUERY)
    fun observeRecords(startInclusive: String, endInclusive: String): Flow<List<AlarmUsageRecordRow>>

    @Query(LOCAL_RECORDS_WITH_CACHE_LINKS_QUERY)
    suspend fun getRecordsWithCacheLinks(
        ownerAccountId: String,
        startInclusive: String,
        endInclusive: String,
    ): List<AlarmUsageRecordWithCacheLink>

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
        require(!history.occurrenceId.isNullOrBlank() && history.occurrenceId == times.occurrenceId)
        // Production initializes this during creation/migration. Keep standalone DAO databases safe too.
        initializeArrivalGrowth(ArrivalGrowthActivationEntity(activatedAtEpochMillis = Clock.System.now().toEpochMilliseconds()))
        val inserted = insert(history)
        mergeOccurrenceTimes(times)
        if (inserted && history.result == MissionResult.SUCCESS.persistedValue) {
            val recordedTimes = getOccurrenceTimes(times.occurrenceId)
            val completedAt = recordedTimes?.missionCompletedAtEpochMillis
            val activatedAt = checkNotNull(getGrowthActivation())
            if (completedAt != null && completedAt >= activatedAt) {
                val grew = !hasSuccessfulDate(history.completedAt)
                val successfulDays = getSuccessfulDayCount() + if (grew) 1 else 0
                insertArrivalResult(
                    ArrivalResultEntity(
                        occurrenceId = times.occurrenceId,
                        completedLocalDate = history.completedAt,
                        completedAtEpochMillis = completedAt,
                        alarmDismissedAtEpochMillis = recordedTimes.ringingStoppedAtEpochMillis,
                        successfulDayCountAtArrival = successfulDays,
                        grewOnThisArrival = grew,
                    ),
                )
                // Persist the random kind and position even when the forest screen is never opened.
                // Failure must roll back the mission, receipt and tree together for a safe retry.
                if (grew) plantCompletedForestTrees()
            }
        }
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

    @Query("SELECT * FROM mission_history WHERE occurrence_id = :occurrenceId LIMIT 1")
    suspend fun getHistoryByOccurrence(occurrenceId: String): MissionHistoryEntity?

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

// Ringing determines the card's date; a stop is the fallback when no start was observed.
// A later start or result enriches that same occurrence;
// legacy results without a ringing event remain visible on their completion date.
private const val LOCAL_RECORD_ROWS = """
        SELECT 'occurrence:' || SUBSTR(a.event_key, 6) AS record_key,
            a.local_date AS record_date, a.alarm_id, a.schedule_version, SUBSTR(a.event_key, 6) AS occurrence_id,
            h.result, COALESCE(t.ringing_started_at, CASE WHEN a.type = 'RANG' THEN a.occurred_at_epoch_millis END) AS ringing_started_at,
            t.ringing_stopped_at, t.mission_completed_at, t.ringing_start_observed, t.ringing_scheduled_at,
            COALESCE(t.ringing_scheduled_at, a.occurred_at_epoch_millis) AS sort_time, 0 AS legacy_id, 1 AS ringing_count
        FROM alarm_activity_events a
        LEFT JOIN alarm_occurrence_times t ON t.occurrence_id = SUBSTR(a.event_key, 6)
        LEFT JOIN mission_history h ON h.occurrence_id = SUBSTR(a.event_key, 6)
        WHERE a.type IN ('RANG', 'RANG_CONFIRMED_BY_STOP')
        UNION ALL
        SELECT COALESCE('occurrence:' || h.occurrence_id, 'history:' || h.id) AS record_key,
            h.completed_at AS record_date, NULL AS alarm_id, 1 AS schedule_version, h.occurrence_id, h.result,
            t.ringing_started_at, t.ringing_stopped_at, t.mission_completed_at, t.ringing_start_observed, t.ringing_scheduled_at,
            COALESCE(t.ringing_scheduled_at, t.ringing_started_at, t.mission_completed_at, 0) AS sort_time,
            h.id AS legacy_id, CASE WHEN t.ringing_started_at IS NULL THEN 0 ELSE 1 END AS ringing_count
        FROM mission_history h
        LEFT JOIN alarm_occurrence_times t ON t.occurrence_id = h.occurrence_id
        WHERE NOT EXISTS (
                SELECT 1 FROM alarm_activity_events a
                WHERE a.type IN ('RANG', 'RANG_CONFIRMED_BY_STOP') AND a.event_key = 'rang:' || h.occurrence_id
            )
"""

private const val ALARM_USAGE_RECORDS_QUERY = """
    SELECT record_key, record_date, alarm_id, occurrence_id, result, ringing_started_at,
        ringing_stopped_at, mission_completed_at, ringing_start_observed, ringing_scheduled_at, schedule_version, ringing_count
    FROM (
""" + LOCAL_RECORD_ROWS + """
    )
    WHERE record_date BETWEEN :startInclusive AND :endInclusive
    ORDER BY record_date ASC, sort_time ASC, legacy_id ASC, record_key ASC
"""

private const val LOCAL_RECORDS_WITH_CACHE_LINKS_QUERY = """
    WITH linked AS (
        SELECT l.local_ringing_id, l.event_id, c.occurrence_id AS server_occurrence_id,
            c.date AS server_date, l.ringing_at AS linked_ringing_at, s.schedule_version,
            dismiss.occurred_at AS dismissed_at, terminal.occurred_at AS ended_at,
            CASE terminal.kind WHEN 'ARRIVED' THEN 'SUCCESS' WHEN 'FORCE_ENDED' THEN 'FAILURE' END AS end_result
        FROM alarm_occurrence_ringing_links l
        JOIN alarm_occurrence_sync s ON s.local_execution_id = l.local_execution_id
        JOIN alarm_occurrence_cache c ON c.owner_account_id = s.owner_account_id
            AND c.occurrence_id = s.server_occurrence_id
        LEFT JOIN alarm_occurrence_outbox dismiss ON dismiss.local_execution_id = s.local_execution_id
            AND dismiss.deduplication_key = 'dismiss:' || l.local_ringing_id
        LEFT JOIN alarm_occurrence_outbox terminal ON terminal.local_execution_id = s.local_execution_id
            AND terminal.deduplication_key = 'terminal' AND terminal.local_ringing_id = l.local_ringing_id
        WHERE s.owner_account_id = :ownerAccountId
    )
    SELECT r.record_key, r.record_date, r.alarm_id, r.occurrence_id, COALESCE(r.result, l.end_result) AS result,
        COALESCE(r.ringing_started_at, l.linked_ringing_at) AS ringing_started_at,
        COALESCE(r.ringing_stopped_at, l.dismissed_at) AS ringing_stopped_at,
        COALESCE(r.mission_completed_at, l.ended_at) AS mission_completed_at, r.ringing_start_observed,
        r.ringing_scheduled_at, r.schedule_version,
        MAX(r.ringing_count, CASE WHEN l.linked_ringing_at IS NOT NULL OR l.dismissed_at IS NOT NULL THEN 1 ELSE 0 END) AS ringing_count,
        l.server_occurrence_id, l.event_id
    FROM (
""" + LOCAL_RECORD_ROWS + """
    ) r
    LEFT JOIN linked l ON l.local_ringing_id = r.occurrence_id
    WHERE COALESCE(l.server_date, r.record_date) BETWEEN :startInclusive AND :endInclusive
    UNION ALL
    SELECT 'occurrence:' || l.local_ringing_id, l.server_date, NULL, l.local_ringing_id, l.end_result,
        l.linked_ringing_at, l.dismissed_at, l.ended_at, NULL, NULL, l.schedule_version,
        CASE WHEN l.linked_ringing_at IS NOT NULL OR l.dismissed_at IS NOT NULL THEN 1 ELSE 0 END,
        l.server_occurrence_id, l.event_id
    FROM linked l
    WHERE l.server_date BETWEEN :startInclusive AND :endInclusive
        AND (l.linked_ringing_at IS NOT NULL OR l.dismissed_at IS NOT NULL OR l.end_result IS NOT NULL)
        AND NOT EXISTS (
            SELECT 1 FROM alarm_activity_events a
            WHERE a.event_key = 'rang:' || l.local_ringing_id AND a.type IN ('RANG', 'RANG_CONFIRMED_BY_STOP')
        )
        AND NOT EXISTS (SELECT 1 FROM mission_history h WHERE h.occurrence_id = l.local_ringing_id)
    ORDER BY record_date ASC, ringing_started_at ASC, record_key ASC
"""
