package com.joon.ringout.data.alarmoccurrence

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction

data class CachedAlarmOccurrenceRow(
    val occurrence: CachedAlarmOccurrenceEntity,
    val ringings: List<CachedAlarmRingingEntity>,
)

data class CachedAlarmOccurrenceRingingRow(
    @Embedded val occurrence: CachedAlarmOccurrenceEntity,
    @Embedded(prefix = "ringing_") val ringing: CachedAlarmRingingEntity,
)

@Dao
abstract class AlarmOccurrenceCacheDao {
    @Query(
        """
        SELECT EXISTS (
            SELECT 1 FROM alarm_occurrence_cache_months
            WHERE owner_account_id = :ownerAccountId AND month = :month
        )
        """,
    )
    abstract suspend fun isComplete(ownerAccountId: String, month: String): Boolean

    @Query("SELECT month FROM alarm_occurrence_cache_months WHERE owner_account_id = :ownerAccountId")
    abstract suspend fun getCompleteMonths(ownerAccountId: String): List<String>

    @Query(
        """
        SELECT o.*, r.owner_account_id AS ringing_owner_account_id,
            r.occurrence_id AS ringing_occurrence_id, r.ringing_key AS ringing_ringing_key,
            r.event_id AS ringing_event_id, r.ringing_at AS ringing_ringing_at,
            r.dismissed_at AS ringing_dismissed_at
        FROM alarm_occurrence_cache o
        INNER JOIN alarm_occurrence_cache_ringings r
            ON r.owner_account_id = o.owner_account_id AND r.occurrence_id = o.occurrence_id
        WHERE o.owner_account_id = :ownerAccountId AND o.date BETWEEN :startInclusive AND :endInclusive
        ORDER BY o.date, r.ringing_at, r.ringing_key
        """,
    )
    protected abstract suspend fun getRingingRows(
        ownerAccountId: String,
        startInclusive: String,
        endInclusive: String,
    ): List<CachedAlarmOccurrenceRingingRow>

    suspend fun getOccurrences(
        ownerAccountId: String,
        startInclusive: String = "",
        endInclusive: String = "9999-12-31",
    ): List<CachedAlarmOccurrenceRow> =
        getRingingRows(ownerAccountId, startInclusive, endInclusive)
            .groupBy(keySelector = { it.occurrence }, valueTransform = { it.ringing })
            .map { (occurrence, ringings) -> CachedAlarmOccurrenceRow(occurrence, ringings) }

    @Insert
    protected abstract suspend fun insertOccurrences(occurrences: List<CachedAlarmOccurrenceEntity>)

    @Insert
    protected abstract suspend fun insertRingings(ringings: List<CachedAlarmRingingEntity>)

    @Insert
    protected abstract suspend fun insertCompleteMonth(month: CachedAlarmOccurrenceMonthEntity)

    @Transaction
    open suspend fun saveMonth(
        ownerAccountId: String,
        month: String,
        occurrences: List<CachedAlarmOccurrenceEntity>,
        ringings: List<CachedAlarmRingingEntity>,
    ) {
        if (isComplete(ownerAccountId, month)) return
        insertOccurrences(occurrences)
        insertRingings(ringings)
        insertCompleteMonth(CachedAlarmOccurrenceMonthEntity(ownerAccountId, month))
    }
}
