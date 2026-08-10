package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.flow.Flow

interface DestinationDataSource {
    fun observeAll(): Flow<List<SavedDestination>>

    suspend fun save(destination: SavedDestination): SavedDestination

    suspend fun updateName(id: Long, name: String): Boolean

    suspend fun delete(id: Long): Boolean
}
