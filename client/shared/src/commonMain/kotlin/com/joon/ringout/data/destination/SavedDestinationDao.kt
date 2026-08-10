package com.joon.ringout.data.destination

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDestinationDao {
    @Query("SELECT * FROM saved_destinations ORDER BY id ASC")
    fun observeAll(): Flow<List<SavedDestinationEntity>>

    @Insert
    suspend fun insert(destination: SavedDestinationEntity): Long

    @Update
    suspend fun update(destination: SavedDestinationEntity): Int

    @Query("UPDATE saved_destinations SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String): Int

    @Query("DELETE FROM saved_destinations WHERE id = :id")
    suspend fun delete(id: Long): Int
}
