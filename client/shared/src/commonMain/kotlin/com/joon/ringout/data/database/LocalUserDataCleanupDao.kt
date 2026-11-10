package com.joon.ringout.data.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
interface LocalUserDataCleanupDao {
    @Query("DELETE FROM alarm_occurrence_outbox")
    suspend fun deleteAlarmOccurrenceOutbox()

    @Query("DELETE FROM alarm_movement_outbox")
    suspend fun deleteAlarmMovementOutbox()

    @Query("DELETE FROM alarm_occurrence_ringing_links")
    suspend fun deleteAlarmOccurrenceRingingLinks()

    @Query("DELETE FROM alarm_occurrence_sync")
    suspend fun deleteAlarmOccurrenceSync()

    @Query("DELETE FROM ios_alarm_occurrence_recording")
    suspend fun deleteIosAlarmOccurrenceRecording()

    @Query("DELETE FROM alarm_ringing_observations")
    suspend fun deleteAlarmRingingObservations()

    @Query("DELETE FROM mission_history")
    suspend fun deleteMissionHistory()

    @Query("DELETE FROM arrival_results")
    suspend fun deleteArrivalResults()

    @Query("DELETE FROM alarm_activity_events")
    suspend fun deleteAlarmActivityEvents()

    @Query("DELETE FROM alarm_activity_tracking")
    suspend fun deleteAlarmActivityTracking()

    @Query("DELETE FROM alarm_occurrence_times")
    suspend fun deleteAlarmOccurrenceTimes()

    @Query("DELETE FROM alarms")
    suspend fun deleteAlarms()

    @Query("DELETE FROM saved_destinations")
    suspend fun deleteSavedDestinations()

    /**
     * Clears only account-owned data. Keep storage_migrations so a later launch cannot
     * import legacy alarm preferences back into this installation. Keep the installation's
     * arrival growth activation timestamp as well.
     */
    @Transaction
    suspend fun clear() {
        deleteAlarmOccurrenceOutbox()
        deleteAlarmMovementOutbox()
        deleteAlarmOccurrenceRingingLinks()
        deleteAlarmOccurrenceSync()
        deleteIosAlarmOccurrenceRecording()
        deleteAlarmRingingObservations()
        deleteArrivalResults()
        deleteMissionHistory()
        deleteAlarmActivityEvents()
        deleteAlarmActivityTracking()
        deleteAlarmOccurrenceTimes()
        // alarm_repeat_days is removed by its existing ON DELETE CASCADE foreign key.
        deleteAlarms()
        deleteSavedDestinations()
    }
}
