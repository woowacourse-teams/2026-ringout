package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.flow.Flow

class DefaultDestinationRepository(
    private val dataSource: DestinationDataSource,
) : DestinationRepository {
    override fun observeAll(): Flow<List<SavedDestination>> = dataSource.observeAll()

    override suspend fun save(destination: SavedDestination): SavedDestination =
        dataSource.save(destination)

    override suspend fun delete(id: Long): Boolean = dataSource.delete(id)
}
