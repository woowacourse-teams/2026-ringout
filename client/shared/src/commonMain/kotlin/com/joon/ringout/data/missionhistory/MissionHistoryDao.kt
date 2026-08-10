package com.joon.ringout.data.missionhistory

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface MissionHistoryDao {
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
