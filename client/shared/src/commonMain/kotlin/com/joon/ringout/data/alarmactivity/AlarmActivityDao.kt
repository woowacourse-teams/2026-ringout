package com.joon.ringout.data.alarmactivity

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

data class AlarmActivityCounts(val ringingCount: Int, val trackingStartDate: String?)

data class AlarmActivityDailyCounts(val localDate: String?, val ringingCount: Int, val trackingStartDate: String?)

@Dao
interface AlarmActivityDao : AlarmOccurrenceTimesAccess {
    @Query("""
        SELECT
            (SELECT COUNT(*) FROM alarm_activity_events WHERE local_date = :date AND type = 'RANG') AS ringingCount,
            (SELECT local_date FROM alarm_activity_tracking WHERE id = 1) AS trackingStartDate
    """)
    fun observeCounts(date: String): Flow<AlarmActivityCounts>

    @Query("""
        SELECT events.local_date AS localDate, COUNT(events.event_key) AS ringingCount,
            tracking.local_date AS trackingStartDate
        FROM alarm_activity_tracking AS tracking
        LEFT JOIN alarm_activity_events AS events
            ON events.local_date IN (:dates) AND events.type = 'RANG'
        WHERE tracking.id = 1
        GROUP BY events.local_date
    """)
    fun observeDailyCounts(dates: List<String>): Flow<List<AlarmActivityDailyCounts>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: AlarmActivityEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun initializeTracking(tracking: AlarmActivityTrackingEntity)

    @Query("UPDATE alarm_activity_tracking SET started_at_epoch_millis = :epochMillis, local_date = :localDate WHERE id = 1 AND started_at_epoch_millis > :epochMillis")
    suspend fun includeEarlierEvent(epochMillis: Long, localDate: String)

    @Query("SELECT * FROM alarm_ringing_observations")
    suspend fun getObservations(): List<AlarmRingingObservationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObservation(observation: AlarmRingingObservationEntity)

    @Query("DELETE FROM alarm_ringing_observations WHERE system_alarm_id = :systemAlarmId")
    suspend fun deleteObservation(systemAlarmId: String)

    @Transaction
    suspend fun record(event: AlarmActivityEntity, isStartObserved: Boolean = false) {
        initializeTracking(AlarmActivityTrackingEntity(startedAtEpochMillis = event.occurredAtEpochMillis, localDate = event.localDate))
        includeEarlierEvent(event.occurredAtEpochMillis, event.localDate)
        insertEvent(event)
        if (event.type == "RANG" && event.eventKey.startsWith("rang:")) {
            mergeOccurrenceTimes(AlarmOccurrenceTimesEntity(
                occurrenceId = event.eventKey.removePrefix("rang:"),
                ringingStartedAtEpochMillis = event.occurredAtEpochMillis,
                isRingingStartObserved = isStartObserved,
            ))
        }
    }

    @Transaction
    suspend fun recordObservedRinging(eventsBySystemId: Map<String, AlarmActivityEntity>) {
        val previous = getObservations().associateBy { it.systemAlarmId }
        previous.keys.filterNot { it in eventsBySystemId }.forEach { deleteObservation(it) }
        eventsBySystemId.forEach { (systemId, event) ->
            if (previous[systemId]?.eventKey != event.eventKey) {
                // A repeating AlarmKit alarm reuses its system ID for a new occurrence.
                if (systemId in previous) deleteObservation(systemId)
                record(event, isStartObserved = true)
                insertObservation(AlarmRingingObservationEntity(systemId, event.eventKey))
            }
        }
    }
}
