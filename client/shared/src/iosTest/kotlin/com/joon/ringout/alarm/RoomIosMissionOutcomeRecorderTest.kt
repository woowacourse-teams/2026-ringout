@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.alarm

import androidx.room3.Room
import com.joon.ringout.data.alarmactivity.AlarmActivityEntity
import com.joon.ringout.data.alarmactivity.AlarmActivityTimestamp
import com.joon.ringout.data.alarmactivity.RoomAlarmActivityRepository
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.presentation.records.recordTimes
import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.RecordMissionResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomIosMissionOutcomeRecorderTest {
    @Test
    fun `첫 울림은 설정 시각이고 재울림은 직전 종료 시각에 반복 간격을 더하며 설정 변경에도 보존한다`() = withDatabase { database ->
        val alarms = RoomAlarmDataSource(database.alarmDao())
        val settings = SavedAlarmSchedule(AlarmScheduleRequest(
            id = "alarm", time = "07:00", selectedDays = listOf("월"), repeatEnabled = true, limitMinutes = 5,
            destinationName = "회사", destinationAddress = "서울", destinationLatitude = 37.5, destinationLongitude = 127.0,
            alarmSoundName = "기본", alarmSoundUri = null,
        ), enabled = true)
        alarms.replace(settings)
        val recorder = recorder(database)
        val firstStop = IosAlarmMissionEventDto(
            "first", "alarm", "one", IosAlarmMissionAction.STOP, localMillis("2026-09-28 07:02"),
            ringingStoppedAtEpochMillis = localMillis("2026-09-28 07:02"),
        )
        recorder.recordRingingTimes(firstStop)
        recorder.recordRetryRingingSchedule("one:retry-1", "one", 5)
        // Scheduling alone must not create a card or increment the count.
        assertEquals(1, database.missionHistoryDao().getRecords("2026-09-28", "2026-09-28").size)
        assertEquals(1, database.alarmActivityDao().observeCounts("2026-09-28").first().ringingCount)

        alarms.replace(settings.copy(request = settings.request.copy(time = "06:00", limitMinutes = 30)))
        recorder.recordRingingTimes(firstStop)
        recorder.recordRingingTimes(IosAlarmMissionEventDto(
            "second", "alarm", "one:retry-1", IosAlarmMissionAction.STOP, localMillis("2026-09-28 07:08"),
            retryAttempt = 1, ringingStoppedAtEpochMillis = localMillis("2026-09-28 07:08"),
        ))
        recorder.recordRetryRingingSchedule("one:retry-2", "one:retry-1", 5)
        recorder.recordRingingTimes(IosAlarmMissionEventDto(
            "third", "alarm", "one:retry-2", IosAlarmMissionAction.STOP, localMillis("2026-09-28 07:14"),
            retryAttempt = 2, ringingObservedAtEpochMillis = localMillis("2026-09-28 07:13") + 20_000,
            ringingStoppedAtEpochMillis = localMillis("2026-09-28 07:14"),
        ))
        alarms.delete("alarm")

        val records = database.missionHistoryDao().getRecords("2026-09-28", "2026-09-28")
        assertEquals(listOf("07:00", "07:07", "07:13").map { localMillis("2026-09-28 $it") }, records.map { it.ringingScheduledAtEpochMillis })
        assertEquals(listOf(null, null, localMillis("2026-09-28 07:13") + 20_000), records.map { it.ringingStartedAtEpochMillis })
        assertEquals(3, database.alarmActivityDao().observeCounts("2026-09-28").first().ringingCount)
        val displayedRecords = RoomMissionHistoryDataSource(database.missionHistoryDao()).getRecords(MissionYearMonth(2026, 9))
        assertEquals(
            listOf("오전 07:00 ~ 오전 07:02", "오전 07:07 ~ 오전 07:08", "오전 07:13 ~ 오전 07:14"),
            displayedRecords.map { it.recordTimes().ringingRange },
        )
    }

    @Test
    fun `자정을 넘겨 종료한 첫 알람은 전날 설정 시각이고 반복 예정 시각은 다음 날이다`() = withDatabase { database ->
        val activity = database.alarmActivityDao()
        activity.recordInitialRingingSchedule("one", "23:58", localMillis("2026-09-29 00:01"))
        val recorder = recorder(database)
        recorder.recordRingingTimes(IosAlarmMissionEventDto(
            "stop", "alarm", "one", IosAlarmMissionAction.STOP, localMillis("2026-09-29 00:01"),
            ringingStoppedAtEpochMillis = localMillis("2026-09-29 00:01"),
        ))
        recorder.recordRetryRingingSchedule("retry", "one", 5)

        assertEquals(localMillis("2026-09-28 23:58"), activity.getOccurrenceTimes("one")?.ringingScheduledAtEpochMillis)
        assertEquals(localMillis("2026-09-29 00:06"), activity.getOccurrenceTimes("retry")?.ringingScheduledAtEpochMillis)
        assertNull(activity.getOccurrenceTimes("one")?.ringingStartedAtEpochMillis)
    }

    @Test
    fun `시작을 놓친 종료도 한 번 집계하고 도착 시각과 함께 재시작 후 복구한다`() = runBlocking {
        val databasePath = NSTemporaryDirectory() + "ringout-stop-only-${NSUUID().UUIDString}.db"
        val stoppedAt = 1_790_544_600_000L
        val date = iosMissionDate(stoppedAt)
        val nextDate = iosMissionDate(stoppedAt + 86_400_000)
        val event = IosAlarmMissionEventDto(
            "stop", "alarm", "one", IosAlarmMissionAction.STOP, stoppedAt,
            ringingStoppedAtEpochMillis = stoppedAt,
        )
        try {
            val database = openDatabase(databasePath)
            try {
                val recorder = recorder(database)
                recorder.recordRingingTimes(event)
                recorder.recordRingingTimes(event.copy(eventId = "redelivered", ringingStoppedAtEpochMillis = stoppedAt + 1_000))

                val pending = database.missionHistoryDao().getRecords(date, date).single()
                assertEquals("alarm", pending.alarmId)
                assertNull(pending.ringingStartedAtEpochMillis)
                assertNull(pending.result)
                assertEquals(stoppedAt, pending.ringingStoppedAtEpochMillis)
                assertEquals(1, database.alarmActivityDao().observeCounts(date).first().ringingCount)

                recorder.recordSuccess("one", nextDate, stoppedAt + 86_400_000)
            } finally {
                database.close()
            }

            val restored = openDatabase(databasePath)
            try {
                val record = restored.missionHistoryDao().getRecords(date, nextDate).single()
                assertEquals("occurrence:one", record.key)
                assertEquals(date, record.date)
                assertNull(record.ringingStartedAtEpochMillis)
                assertEquals(stoppedAt, record.ringingStoppedAtEpochMillis)
                assertEquals(stoppedAt + 86_400_000, record.missionCompletedAtEpochMillis)
                assertEquals("SUCCESS", record.result)
                val summaries = RoomAlarmActivityRepository(restored.alarmActivityDao())
                    .observeSummaries(listOf(MissionDate.parse(date), MissionDate.parse(nextDate))).first()
                assertEquals(listOf(1, 0), summaries.values.map { it.ringingCount })
                assertTrue(restored.missionHistoryDao().getRecords(nextDate, nextDate).isEmpty())
            } finally {
                restored.close()
            }
        } finally {
            deleteDatabaseFiles(databasePath)
        }
    }

    @Test
    fun `늦게 확인된 시작은 기존 종료 기록에 합치고 원래 울린 날짜로 집계를 옮긴다`() = withDatabase { database ->
        val stoppedAt = 1_790_544_600_000L
        val startedAt = stoppedAt - 86_400_000
        val stopDate = iosMissionDate(stoppedAt)
        val startDate = iosMissionDate(startedAt)
        val activity = database.alarmActivityDao()
        val recorder = recorder(database)
        val stop = IosAlarmMissionEventDto(
            "stop", "alarm", "one", IosAlarmMissionAction.STOP, stoppedAt,
            ringingStoppedAtEpochMillis = stoppedAt,
        )
        recorder.recordRingingTimes(stop)
        recorder.recordFailure("one", stopDate, stoppedAt + 1_000)
        val originalKey = database.missionHistoryDao().getRecords(stopDate, stopDate).single().key

        activity.recordObservedRinging(mapOf("system-alarm" to AlarmActivityEntity.rang(
            "alarm", "one", AlarmActivityTimestamp(startedAt, startDate),
        )))
        recorder.recordRingingTimes(stop.copy(eventId = "late-start", ringingObservedAtEpochMillis = startedAt))
        recorder.recordRingingTimes(stop)

        val record = database.missionHistoryDao().getRecords(startDate, stopDate).single()
        assertEquals(originalKey, record.key)
        assertEquals(startDate, record.date)
        assertEquals(startedAt, record.ringingStartedAtEpochMillis)
        assertEquals(stoppedAt, record.ringingStoppedAtEpochMillis)
        assertEquals(stoppedAt + 1_000, record.missionCompletedAtEpochMillis)
        assertEquals("FAILURE", record.result)
        assertEquals(true, record.isRingingStartObserved)
        val summaries = RoomAlarmActivityRepository(activity)
            .observeSummaries(listOf(MissionDate.parse(startDate), MissionDate.parse(stopDate))).first()
        assertEquals(listOf(1, 0), summaries.values.map { it.ringingCount })
        assertEquals(1, activity.observeCounts(startDate).first().ringingCount)
        assertEquals(0, activity.observeCounts(stopDate).first().ringingCount)
    }

    @Test
    fun `시작을 먼저 기록했다면 다음 날 종료돼도 울린 날짜와 시작 시각을 유지한다`() = withDatabase { database ->
        val startedAt = 1_790_544_600_000L
        val stoppedAt = startedAt + 86_400_000
        val startDate = iosMissionDate(startedAt)
        val stopDate = iosMissionDate(stoppedAt)
        val activity = database.alarmActivityDao()
        val recorder = recorder(database)
        recorder.recordRingingTimes(IosAlarmMissionEventDto(
            "start", "alarm", "one", IosAlarmMissionAction.OPEN_APP, startedAt,
            ringingObservedAtEpochMillis = startedAt,
        ))
        val stop = IosAlarmMissionEventDto(
            "stop", "alarm", "one", IosAlarmMissionAction.STOP, stoppedAt,
            ringingStoppedAtEpochMillis = stoppedAt,
        )
        repeat(2) { recorder.recordRingingTimes(stop) }

        val record = database.missionHistoryDao().getRecords(startDate, stopDate).single()
        assertEquals(startDate, record.date)
        assertEquals(startedAt, record.ringingStartedAtEpochMillis)
        assertEquals(stoppedAt, record.ringingStoppedAtEpochMillis)
        assertEquals(1, activity.observeCounts(startDate).first().ringingCount)
        assertEquals(0, activity.observeCounts(stopDate).first().ringingCount)
    }

    @Test
    fun `시작과 종료를 모두 확인하지 못한 이벤트는 울림 횟수로 추정하지 않는다`() = withDatabase { database ->
        val recorder = recorder(database)
        IosAlarmMissionAction.entries.forEach { action ->
            recorder.recordRingingTimes(IosAlarmMissionEventDto("event", "alarm", "one", action, 2_000))
        }

        val date = iosMissionDate(2_000L)
        assertTrue(database.missionHistoryDao().getRecords(date, date).isEmpty())
        assertEquals(0, database.alarmActivityDao().observeCounts(date).first().ringingCount)
        assertNull(database.alarmActivityDao().getOccurrenceTimes("one"))
    }

    @Test
    fun `실행별 시각과 성공 기록을 중복 없이 보존하고 데이터베이스를 다시 열어도 유지한다`() = runBlocking {
        val databasePath = NSTemporaryDirectory() +
            "ringout-outcome-${NSUUID().UUIDString}.db"
        try {
            val firstDatabase = openDatabase(databasePath)
            val recorder = recorder(firstDatabase)
            recorder.recordRingingTimes(IosAlarmMissionEventDto(
                "stop-event", "alarm", "success-occurrence", IosAlarmMissionAction.STOP, 2_000,
                ringingObservedAtEpochMillis = 1_000,
                ringingStoppedAtEpochMillis = 2_000,
            ))

            recorder.recordSuccess("success-occurrence", "2026-08-05", 3_000)
            recorder.recordFailure("success-occurrence", "2026-08-05")
            recorder.recordFailure("same-day-failure", "2026-08-05")
            recorder.recordFailure("failure-only", "2026-08-06")
            firstDatabase.close()

            val restoredDatabase = openDatabase(databasePath)
            try {
                val restoredRepository = repository(restoredDatabase)
                val timed = restoredRepository.getHistory(MissionYearMonth(2026, 8)).first()
                assertEquals(1_000L, timed.ringingStartedAtEpochMillis)
                assertEquals(2_000L, timed.ringingStoppedAtEpochMillis)
                assertEquals(3_000L, timed.missionCompletedAtEpochMillis)
                assertEquals(true, timed.isRingingStartObserved)
                assertEquals(
                    setOf(MissionDate.parse("2026-08-05")),
                    GetMissionSuccessDates(restoredRepository)(MissionYearMonth(2026, 8)),
                )
                assertEquals(
                    listOf(
                        "success-occurrence" to MissionResult.SUCCESS,
                        "same-day-failure" to MissionResult.FAILURE,
                        "failure-only" to MissionResult.FAILURE,
                    ),
                    restoredRepository.getHistory(MissionYearMonth(2026, 8))
                        .map { entry -> entry.occurrenceId to entry.result },
                )
            } finally {
                restoredDatabase.close()
            }
        } finally {
            deleteDatabaseFiles(databasePath)
        }
    }

    private fun openDatabase(path: String): RingoutDatabase = buildRingoutDatabase(
        Room.databaseBuilder<RingoutDatabase>(name = path),
    )

    private fun recorder(database: RingoutDatabase) = RoomIosMissionOutcomeRecorder(
        RecordMissionResult(repository(database)), database.alarmActivityDao(), RoomAlarmDataSource(database.alarmDao()),
    )

    private fun localMillis(text: String): Long {
        val formatter = NSDateFormatter().apply {
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
            dateFormat = "yyyy-MM-dd HH:mm"
        }
        return ((requireNotNull(formatter.dateFromString(text)).timeIntervalSinceReferenceDate + 978_307_200.0) * 1_000).toLong()
    }

    private fun withDatabase(block: suspend (RingoutDatabase) -> Unit) = runBlocking {
        val database = buildRingoutDatabase(Room.inMemoryDatabaseBuilder<RingoutDatabase>())
        try { block(database) } finally { database.close() }
    }

    private fun repository(database: RingoutDatabase) = DefaultMissionHistoryRepository(
        RoomMissionHistoryDataSource(database.missionHistoryDao()),
    )

    private fun deleteDatabaseFiles(databasePath: String) {
        listOf(databasePath, "$databasePath-shm", "$databasePath-wal").forEach { path ->
            if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
                NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            }
        }
    }
}
