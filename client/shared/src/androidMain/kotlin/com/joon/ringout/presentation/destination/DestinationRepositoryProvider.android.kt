package com.joon.ringout.presentation.destination

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.joon.ringout.data.auth.local.rememberSecureTokenStorage
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.destination.DefaultDestinationRepository
import com.joon.ringout.data.destination.KtorDestinationRemoteDataSource
import com.joon.ringout.data.destination.RoomDestinationDataSource
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.domain.destination.DestinationRepository

@Composable
internal actual fun rememberDestinationRepository(): DestinationRepository {
    val applicationContext = LocalContext.current.applicationContext
    val tokenStorage = rememberSecureTokenStorage()
    val httpClient = remember { getRingoutHttpClient() }
    return remember(applicationContext, httpClient, tokenStorage) {
        DefaultDestinationRepository(
            dataSource = RoomDestinationDataSource(
                getRingoutDatabase(applicationContext).destinationDao(),
            ),
            remoteDataSource = KtorDestinationRemoteDataSource(httpClient, tokenStorage),
        )
    }
}
