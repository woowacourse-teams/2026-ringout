package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository

@Composable
internal actual fun rememberMissionHistoryRepository(): MissionHistoryRepository {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        DefaultMissionHistoryRepository(
            RoomMissionHistoryDataSource(
                getRingoutDatabase(applicationContext).missionHistoryDao(),
            ),
        )
    }
}
