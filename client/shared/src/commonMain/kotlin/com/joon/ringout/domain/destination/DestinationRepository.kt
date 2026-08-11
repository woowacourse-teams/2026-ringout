package com.joon.ringout.domain.destination

import kotlinx.coroutines.flow.Flow

interface DestinationRepository {
    fun observeAll(): Flow<List<SavedDestination>>

    suspend fun save(destination: SavedDestination): SavedDestination

    suspend fun updateName(id: Long, name: String): Boolean

    suspend fun delete(id: Long): Boolean
}
