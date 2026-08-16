package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class DefaultDestinationRepository(
    private val dataSource: DestinationDataSource,
    private val remoteDataSource: DestinationRemoteDataSource? = null,
) : DestinationRepository {
    override fun observeAll(): Flow<List<SavedDestination>> = dataSource.observeAll()

    override suspend fun fetchAll(): List<SavedDestination> =
        remoteDataSource?.fetchAll() ?: dataSource.observeAll().first()

    override suspend fun save(destination: SavedDestination): SavedDestination =
        remoteDataSource?.create(destination) ?: dataSource.save(destination)

    override suspend fun updateName(id: Long, name: String): Boolean =
        dataSource.updateName(id, name)

    override suspend fun delete(id: Long): Boolean = dataSource.delete(id)
}
