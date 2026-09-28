package com.joon.ringout.alarm

import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.alarmactivity.AlarmActivityDao
import com.joon.ringout.data.alarmactivity.AlarmActivityEntity
import com.joon.ringout.data.alarmactivity.AlarmActivityTimestamp
import com.joon.ringout.data.alarmactivity.AlarmOccurrenceTimesEntity
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.RecordMissionResult
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

internal class RoomIosMissionOutcomeRecorder(
    private val recordMissionResult: RecordMissionResult = RecordMissionResult(
        DefaultMissionHistoryRepository(
            dataSource = RoomMissionHistoryDataSource(getRingoutDatabase().missionHistoryDao()),
            // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 원격 미션 기록 저장소를 다시 주입한다.
        ),
    ),
    private val activityDao: AlarmActivityDao = getRingoutDatabase().alarmActivityDao(),
) : IosMissionOutcomeRecorder {

    override suspend fun recordRingingTimes(event: IosAlarmMissionEventDto) {
        event.ringingObservedAtEpochMillis?.let { startedAt ->
            activityDao.record(
                AlarmActivityEntity.rang(event.alarmId, event.occurrenceId, AlarmActivityTimestamp(startedAt, iosMissionDate(startedAt))),
                isStartObserved = true,
            )
        }
        activityDao.mergeOccurrenceTimes(AlarmOccurrenceTimesEntity(
            occurrenceId = event.occurrenceId,
            ringingStoppedAtEpochMillis = event.ringingStoppedAtEpochMillis,
        ))
    }

    override suspend fun recordSuccess(occurrenceId: String, completedAt: String, completedAtEpochMillis: Long?) {
        recordMissionResult(MissionResult.SUCCESS, MissionDate.parse(completedAt), occurrenceId, completedAtEpochMillis)
    }

    override suspend fun recordFailure(occurrenceId: String, completedAt: String, completedAtEpochMillis: Long?) {
        recordMissionResult(MissionResult.FAILURE, MissionDate.parse(completedAt), occurrenceId, completedAtEpochMillis)
    }
}

internal fun iosMissionDate(epochMillis: Double): String {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd"
    }
    return formatter.stringFromDate(
        NSDate(
            timeIntervalSinceReferenceDate =
                epochMillis / 1_000.0 - SecondsFromUnixEpochToAppleReferenceDate,
        ),
    )
}

internal fun iosMissionDate(epochMillis: Long): String = iosMissionDate(epochMillis.toDouble())

private const val SecondsFromUnixEpochToAppleReferenceDate = 978_307_200.0
