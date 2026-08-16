package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth

class DefaultMissionHistoryRepository(
    private val dataSource: MissionHistoryDataSource,
    private val remoteDataSource: MissionHistoryRemoteDataSource? = null,
) : MissionHistoryRepository {
    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> {
        val remote = remoteDataSource
        val history = if (remote != null && remote.hasAccessToken()) {
            remote.getHistory(month)
        } else {
            dataSource.getHistory(month)
        }
        return history.map(MissionHistoryDto::toDomain)
    }

    override suspend fun record(entry: MissionHistoryEntry): Boolean {
        require(!entry.occurrenceId.isNullOrBlank()) {
            "Mission occurrence ID must not be blank."
        }
        val remote = remoteDataSource
        return if (remote != null && remote.hasAccessToken()) {
            when (entry.result) {
                MissionResult.SUCCESS -> remote.recordSuccess(entry.completedAt)
                MissionResult.FAILURE -> remote.recordFailure(entry.completedAt)
            }
        } else {
            dataSource.record(entry.toDto())
        }
    }
}
