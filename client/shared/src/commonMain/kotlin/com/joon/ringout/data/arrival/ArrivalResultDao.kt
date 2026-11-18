package com.joon.ringout.data.arrival

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/** Also used by the mission DAO inside its existing result transaction. */
interface ArrivalResultWriteAccess {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun initializeArrivalGrowth(activation: ArrivalGrowthActivationEntity)

    @Query("SELECT activated_at_epoch_millis FROM arrival_growth_activation WHERE id = 1")
    suspend fun getGrowthActivation(): Long?

    @Query("SELECT COUNT(DISTINCT completed_local_date) FROM arrival_results")
    suspend fun getSuccessfulDayCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM arrival_results WHERE completed_local_date = :localDate)")
    suspend fun hasSuccessfulDate(localDate: String): Boolean

    // A failure here must roll back the mission record as well, so retries cannot lose the receipt.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArrivalResult(result: ArrivalResultEntity)
}

@Dao
interface ArrivalResultDao : ArrivalResultWriteAccess {
    @Query("SELECT * FROM arrival_results WHERE occurrence_id = :occurrenceId")
    suspend fun getResult(occurrenceId: String): ArrivalResultEntity?

    @Query("""
        SELECT * FROM arrival_results
        WHERE acknowledged_at_epoch_millis IS NULL
        ORDER BY completed_at_epoch_millis DESC, occurrence_id DESC
        LIMIT 1
    """)
    fun observeLatestUnacknowledgedResult(): Flow<ArrivalResultEntity?>

    @Query("SELECT COUNT(DISTINCT completed_local_date) FROM arrival_results")
    fun observeSuccessfulDayCount(): Flow<Int>

    @Query("""
        UPDATE arrival_results SET acknowledged_at_epoch_millis = :acknowledgedAtEpochMillis
        WHERE acknowledged_at_epoch_millis IS NULL
            AND completed_at_epoch_millis <= (
                SELECT completed_at_epoch_millis FROM arrival_results WHERE occurrence_id = :occurrenceId
            )
    """)
    suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long)
}
