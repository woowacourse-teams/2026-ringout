package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.flow.Flow

interface DestinationDataSource {
    fun observeAll(ownerAccountId: String? = null): Flow<List<SavedDestination>>

    suspend fun save(destination: SavedDestination, ownerAccountId: String? = null): SavedDestination

    suspend fun delete(id: Long, ownerAccountId: String? = null): Boolean
}
