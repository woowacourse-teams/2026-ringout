package com.joon.ringout.presentation.destination

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.joon.ringout.data.auth.local.rememberSecureTokenStorage
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.destination.DefaultDestinationRepository
import com.joon.ringout.data.destination.KtorDestinationRemoteDataSource
import com.joon.ringout.data.destination.RoomDestinationDataSource
import com.joon.ringout.data.network.createRingoutHttpClient
import com.joon.ringout.domain.destination.DestinationRepository

@Composable
internal actual fun rememberDestinationRepository(): DestinationRepository {
    val tokenStorage = rememberSecureTokenStorage()
    val httpClient = remember { createRingoutHttpClient() }
    DisposableEffect(httpClient) {
        onDispose(httpClient::close)
    }
    return remember(httpClient, tokenStorage) {
        DefaultDestinationRepository(
            dataSource = RoomDestinationDataSource(getRingoutDatabase().destinationDao()),
            remoteDataSource = KtorDestinationRemoteDataSource(httpClient, tokenStorage),
        )
    }
}
