package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joon.ringout.data.auth.local.rememberSecureTokenStorage
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.KtorMissionHistoryRemoteDataSource
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.data.network.getRingoutHttpClient
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository

@Composable
internal actual fun rememberMissionHistoryRepository(): MissionHistoryRepository {
    val tokenStorage = rememberSecureTokenStorage()
    val httpClient = remember { getRingoutHttpClient() }
    return remember(httpClient, tokenStorage) {
        DefaultMissionHistoryRepository(
            dataSource = RoomMissionHistoryDataSource(getRingoutDatabase().missionHistoryDao()),
            remoteDataSource = KtorMissionHistoryRemoteDataSource(httpClient, tokenStorage),
        )
    }
}
