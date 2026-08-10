package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository

@Composable
internal actual fun rememberMissionHistoryRepository(): MissionHistoryRepository = remember {
    DefaultMissionHistoryRepository(
        RoomMissionHistoryDataSource(getRingoutDatabase().missionHistoryDao()),
    )
}
