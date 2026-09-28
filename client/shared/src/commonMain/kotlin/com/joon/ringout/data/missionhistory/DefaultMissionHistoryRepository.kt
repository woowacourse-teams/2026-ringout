package com.joon.ringout.data.missionhistory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord

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

    override suspend fun getLocalHistory(month: MissionYearMonth): List<MissionHistoryEntry> =
        dataSource.getHistory(month).map(MissionHistoryDto::toDomain)

    override fun observeLocalHistory(month: MissionYearMonth): Flow<List<MissionHistoryEntry>> =
        dataSource.observeHistory(month).map { history -> history.map(MissionHistoryDto::toDomain) }

    override suspend fun getLocalRecords(month: MissionYearMonth): List<AlarmUsageRecord> =
        dataSource.getRecords(month)

    override fun observeLocalRecords(month: MissionYearMonth): Flow<List<AlarmUsageRecord>> =
        dataSource.observeRecords(month)

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
