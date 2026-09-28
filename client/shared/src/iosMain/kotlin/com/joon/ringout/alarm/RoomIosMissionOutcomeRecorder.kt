package com.joon.ringout.alarm

import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.alarmactivity.AlarmActivityDao
import com.joon.ringout.data.alarmactivity.AlarmActivityEntity
import com.joon.ringout.data.alarmactivity.AlarmActivityTimestamp
import com.joon.ringout.data.alarmactivity.AlarmOccurrenceTimesEntity
import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.data.alarm.RoomAlarmDataSource
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
    private val alarmDataSource: AlarmDataSource = RoomAlarmDataSource(getRingoutDatabase().alarmDao()),
) : IosMissionOutcomeRecorder {

    override suspend fun recordRingingTimes(event: IosAlarmMissionEventDto) {
        val referenceTime = event.ringingObservedAtEpochMillis ?: event.ringingStoppedAtEpochMillis
        if (event.retryAttempt == 0 && referenceTime != null) {
            alarmDataSource.getById(event.alarmId)?.request?.time?.let { alarmTime ->
                activityDao.recordInitialRingingSchedule(event.occurrenceId, alarmTime, referenceTime)
            }
        }
        event.ringingObservedAtEpochMillis?.let { startedAt ->
            activityDao.record(
                AlarmActivityEntity.rang(event.alarmId, event.occurrenceId, AlarmActivityTimestamp(startedAt, iosMissionDate(startedAt)), event.scheduleVersion),
                isStartObserved = true,
            )
        }
        event.ringingStoppedAtEpochMillis?.let { stoppedAt ->
            activityDao.record(
                AlarmActivityEntity.rangConfirmedByStop(
                    event.alarmId, event.occurrenceId, AlarmActivityTimestamp(stoppedAt, iosMissionDate(stoppedAt)),
                    scheduleVersion = event.scheduleVersion,
                ),
            )
        }
    }

    override suspend fun recordRetryRingingSchedule(occurrenceId: String, sourceOccurrenceId: String, intervalMinutes: Int) {
        val stoppedAt = activityDao.getOccurrenceTimes(sourceOccurrenceId)?.ringingStoppedAtEpochMillis ?: return
        activityDao.mergeOccurrenceTimes(AlarmOccurrenceTimesEntity(
            occurrenceId = occurrenceId,
            ringingScheduledAtEpochMillis = stoppedAt + intervalMinutes * 60_000L,
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
