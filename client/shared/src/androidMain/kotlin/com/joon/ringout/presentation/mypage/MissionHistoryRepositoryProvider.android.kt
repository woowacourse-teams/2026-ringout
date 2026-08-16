package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.joon.ringout.data.auth.local.rememberSecureTokenStorage
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.KtorMissionHistoryRemoteDataSource
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository

@Composable
internal actual fun rememberMissionHistoryRepository(): MissionHistoryRepository {
    val applicationContext = LocalContext.current.applicationContext
    val tokenStorage = rememberSecureTokenStorage()
    val httpClient = remember { getRingoutHttpClient() }
    return remember(applicationContext, httpClient, tokenStorage) {
        DefaultMissionHistoryRepository(
            dataSource = RoomMissionHistoryDataSource(
                getRingoutDatabase(applicationContext).missionHistoryDao(),
            ),
            remoteDataSource = KtorMissionHistoryRemoteDataSource(httpClient, tokenStorage),
        )
    }
}
