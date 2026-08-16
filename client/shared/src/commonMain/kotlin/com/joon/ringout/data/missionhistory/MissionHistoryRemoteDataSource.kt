package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.MissionDate

interface MissionHistoryRemoteDataSource {
    suspend fun hasAccessToken(): Boolean

    suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto>

    suspend fun recordSuccess(completedAt: MissionDate): Boolean

    suspend fun recordFailure(terminatedAt: MissionDate): Boolean
}
