package com.joon.ringout.data.missionhistory

import androidx.room3.Dao
import androidx.room3.Insert
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

    @Insert
    suspend fun insert(history: MissionHistoryEntity): Long
}
