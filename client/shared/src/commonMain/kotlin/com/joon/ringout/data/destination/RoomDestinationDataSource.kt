package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDestinationDataSource(
    private val destinationDao: SavedDestinationDao,
) : DestinationDataSource {
    override fun observeAll(ownerAccountId: String?): Flow<List<SavedDestination>> =
        destinationDao.observeAll(ownerAccountId).map { destinations ->
            destinations.map(SavedDestinationEntity::toDomain)
        }

    override suspend fun save(destination: SavedDestination, ownerAccountId: String?): SavedDestination {
        val entity = destination.toEntity()
        return if (entity.id == 0L) {
            destinationDao.saveNew(entity, ownerAccountId).toDomain()
        } else {
            error("Saved destinations are immutable once created: ${entity.id}")
        }
    }

    override suspend fun delete(id: Long, ownerAccountId: String?): Boolean =
        destinationDao.deleteLocal(id, ownerAccountId)
}
