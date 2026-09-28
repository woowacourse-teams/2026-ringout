package com.joon.ringout.data.missionhistory

import com.joon.ringout.data.alarmactivity.AlarmOccurrenceTimesEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord

class RoomMissionHistoryDataSource(
    private val missionHistoryDao: MissionHistoryDao,
) : MissionHistoryDataSource {
    override suspend fun getRecords(month: MissionYearMonth): List<AlarmUsageRecord> =
        missionHistoryDao.getRecords(
            MissionDate.of(month.year, month.month, 1).iso8601,
            MissionDate.of(month.year, month.month, month.dayCount).iso8601,
        ).map(AlarmUsageRecordRow::toDomain)

    override fun observeRecords(month: MissionYearMonth): Flow<List<AlarmUsageRecord>> =
        missionHistoryDao.observeRecords(
            MissionDate.of(month.year, month.month, 1).iso8601,
            MissionDate.of(month.year, month.month, month.dayCount).iso8601,
        ).map { rows -> rows.map(AlarmUsageRecordRow::toDomain) }

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto> {
        val firstDay = MissionDate.of(month.year, month.month, 1).iso8601
        val lastDay = MissionDate.of(month.year, month.month, month.dayCount).iso8601
        return missionHistoryDao.getHistoryWithTimes(firstDay, lastDay).map(MissionHistoryWithTimes::toDto)
    }

    override fun observeHistory(month: MissionYearMonth): Flow<List<MissionHistoryDto>> =
        missionHistoryDao.observeHistoryWithTimes(
            MissionDate.of(month.year, month.month, 1).iso8601,
            MissionDate.of(month.year, month.month, month.dayCount).iso8601,
        ).map { entries -> entries.map(MissionHistoryWithTimes::toDto) }

    override suspend fun record(history: MissionHistoryDto): Boolean {
        require(!history.occurrenceId.isNullOrBlank()) {
            "Mission occurrence ID must not be blank."
        }
        return missionHistoryDao.insertWithTimes(
            history.toEntity(),
            AlarmOccurrenceTimesEntity(
                occurrenceId = history.occurrenceId,
                ringingStartedAtEpochMillis = history.ringingStartedAtEpochMillis,
                ringingStoppedAtEpochMillis = history.ringingStoppedAtEpochMillis,
                missionCompletedAtEpochMillis = history.missionCompletedAtEpochMillis,
                isRingingStartObserved = history.isRingingStartObserved,
            ),
        )
    }
}
