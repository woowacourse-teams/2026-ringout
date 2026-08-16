package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.SavedDestination

interface DestinationRemoteDataSource {
    suspend fun fetchAll(): List<SavedDestination>

    suspend fun create(destination: SavedDestination): SavedDestination

    suspend fun updateName(id: Long, name: String): Boolean

    suspend fun delete(id: Long): Boolean
}
