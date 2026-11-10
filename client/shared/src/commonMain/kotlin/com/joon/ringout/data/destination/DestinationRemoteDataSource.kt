package com.joon.ringout.data.destination

/** Stable server identity and payload used by destination synchronization. */
data class RemoteSavedDestination(
    val id: String,
    val alias: String,
    val latitude: Double,
    val longitude: Double,
    val version: Long,
)

interface DestinationRemoteDataSource {
    suspend fun fetchAll(ownerAccountId: String): List<RemoteSavedDestination>

    suspend fun create(
        ownerAccountId: String,
        destination: RemoteSavedDestination,
    ): RemoteSavedDestination

    suspend fun delete(ownerAccountId: String, destinationId: String)
}
