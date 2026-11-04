package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDestinationDataSource(
    private val destinationDao: SavedDestinationDao,
) : DestinationDataSource {
    override fun observeAll(): Flow<List<SavedDestination>> =
        destinationDao.observeAll().map { destinations ->
            destinations.map(SavedDestinationEntity::toDomain)
        }

    override suspend fun save(destination: SavedDestination): SavedDestination {
        val entity = destination.toEntity()
        return if (entity.id == 0L) {
            destination.copy(id = destinationDao.insert(entity))
        } else {
            error("Saved destinations are immutable once created: ${entity.id}")
        }
    }

    override suspend fun delete(id: Long): Boolean {
        require(id >= 0L) { "Saved destination id must not be negative: $id" }
        return destinationDao.delete(id) == 1
    }
}
