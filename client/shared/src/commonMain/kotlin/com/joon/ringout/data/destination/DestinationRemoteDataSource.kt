package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.SavedDestination

interface DestinationRemoteDataSource {
    suspend fun create(destination: SavedDestination): SavedDestination
}
