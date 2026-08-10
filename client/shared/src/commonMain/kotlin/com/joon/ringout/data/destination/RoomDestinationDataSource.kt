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
            check(destinationDao.update(entity) == 1) {
                "Saved destination does not exist: ${entity.id}"
            }
            destination
        }
    }

    override suspend fun updateName(id: Long, name: String): Boolean {
        require(id >= 0L) { "Saved destination id must not be negative: $id" }
        require(name.isNotBlank()) { "Saved destination name must not be blank." }
        return destinationDao.updateName(id, name) == 1
    }

    override suspend fun delete(id: Long): Boolean {
        require(id >= 0L) { "Saved destination id must not be negative: $id" }
        return destinationDao.delete(id) == 1
    }
}
