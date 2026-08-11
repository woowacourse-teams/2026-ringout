package com.joon.ringout.presentation.mypage

import androidx.compose.runtime.Composable
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository

@Composable
internal expect fun rememberMissionHistoryRepository(): MissionHistoryRepository
