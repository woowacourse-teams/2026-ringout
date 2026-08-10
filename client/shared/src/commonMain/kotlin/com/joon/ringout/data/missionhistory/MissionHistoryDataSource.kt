package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionYearMonth

interface MissionHistoryDataSource {
    suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto>
}
