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

    override suspend fun fetchAll(): List<SavedDestination> {
        val remote = remoteDataSource
        return if (remote != null && remote.hasAccessToken()) {
            remote.fetchAll()
        } else {
            dataSource.observeAll().first()
        }
    }

    override suspend fun sync(): List<SavedDestination> {
        val localDestinations = dataSource.observeAll().first()
        return remoteDataSource?.sync(localDestinations) ?: localDestinations
    }

    override suspend fun save(destination: SavedDestination): SavedDestination {
        val remote = remoteDataSource
        return if (remote != null && remote.hasAccessToken()) {
            remote.create(destination)
        } else {
            dataSource.save(destination)
        }
    }

    override suspend fun updateName(id: Long, name: String): Boolean {
        val remote = remoteDataSource
        return if (remote != null && remote.hasAccessToken()) {
            remote.updateName(id, name)
        } else {
            dataSource.updateName(id, name)
        }
    }

    override suspend fun delete(id: Long): Boolean {
        val remote = remoteDataSource
        return if (remote != null && remote.hasAccessToken()) {
            remote.delete(id)
        } else {
            dataSource.delete(id)
        }
    }
}
