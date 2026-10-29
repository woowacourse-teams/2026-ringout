package com.joon.ringout.data.destination

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDestinationDao {
    @Query("SELECT * FROM saved_destinations ORDER BY id ASC")
    fun observeAll(): Flow<List<SavedDestinationEntity>>

    @Insert
    suspend fun insert(destination: SavedDestinationEntity): Long

    @Query("DELETE FROM saved_destinations WHERE id = :id")
    suspend fun delete(id: Long): Int
}
