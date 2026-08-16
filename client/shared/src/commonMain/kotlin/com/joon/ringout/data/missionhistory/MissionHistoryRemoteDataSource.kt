package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionYearMonth

interface MissionHistoryRemoteDataSource {
    suspend fun hasAccessToken(): Boolean

    suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto>
}
