package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.SavedDestination

/** Stable server identity and payload used by destination synchronization. */
data class RemoteSavedDestination(
    val id: String,
    val alias: String,
    val latitude: Double,
    val longitude: Double,
    val version: Long,
)

interface DestinationRemoteDataSource {
    suspend fun hasAccessToken(): Boolean

    suspend fun fetchAll(): List<SavedDestination>

    suspend fun sync(destinations: List<SavedDestination>): List<SavedDestination>

    suspend fun create(destination: SavedDestination): SavedDestination

    suspend fun delete(id: Long): Boolean
}
