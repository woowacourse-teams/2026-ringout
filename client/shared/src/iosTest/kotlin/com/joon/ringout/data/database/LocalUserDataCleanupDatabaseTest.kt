package com.joon.ringout.data.database

import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.async.step
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.alarm.AlarmEntity
import com.joon.ringout.data.alarm.AlarmRepeatDayEntity
import com.joon.ringout.data.alarm.StorageMigrationEntity
import com.joon.ringout.data.alarmactivity.AlarmActivityEntity
import com.joon.ringout.data.alarmactivity.AlarmActivityTimestamp
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncEntity
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxKind
import com.joon.ringout.data.alarmoccurrence.IosAlarmOccurrenceRecordEntity
import com.joon.ringout.data.alarmoccurrence.IosAlarmOccurrenceRecordState
import com.joon.ringout.data.arrival.ArrivalGrowthActivationEntity
import com.joon.ringout.data.arrival.ArrivalResultEntity
import com.joon.ringout.data.destination.SavedDestinationEntity
import com.joon.ringout.data.missionhistory.MissionHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalUserDataCleanupDatabaseTest {
    @Test
    fun `로그아웃 정리는 목적지와 동기화 대기열을 지우고 설치 표식은 보존한다`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<RingoutDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        try {
            val alarmDao = database.alarmDao()
            val activityDao = database.alarmActivityDao()
            val occurrenceDao = database.alarmOccurrenceSyncDao()
            val missionHistoryDao = database.missionHistoryDao()
            val arrivalDao = database.arrivalResultDao()
            arrivalDao.initializeArrivalGrowth(ArrivalGrowthActivationEntity(activatedAtEpochMillis = 1_000))
            val growthActivation = arrivalDao.getGrowthActivation()
            arrivalDao.insertArrivalResult(
                ArrivalResultEntity(
                    occurrenceId = "execution-1",
                    completedLocalDate = "2026-11-09",
                    completedAtEpochMillis = 1_300,
                    alarmDismissedAtEpochMillis = 1_200,
                    successfulDayCountAtArrival = 1,
                    grewOnThisArrival = true,
                ),
            )
            assertEquals(1, arrivalDao.getSuccessfulDayCount())

            alarmDao.insertAlarm(sampleAlarm())
            alarmDao.insertRepeatDays(listOf(AlarmRepeatDayEntity("alarm-1", 1)))
            alarmDao.insertStorageMigration(StorageMigrationEntity("legacy-import", 1L))
            assertEquals(listOf(1), alarmDao.getById("alarm-1")?.repeatDays?.map { it.dayOfWeek })
            val destinationDao = database.destinationDao()
            destinationDao.saveNew(
                SavedDestinationEntity(label = "회사", latitude = 37.5, longitude = 127.0),
                ownerAccountId = "account-1",
            )
            destinationDao.prepareAccount("account-1")

            occurrenceDao.recordStart(
                AlarmOccurrenceSyncEntity(
                    localExecutionId = "execution-1",
                    ownerAccountId = "account-1",
                    alarmId = "alarm-1",
                    scheduleVersion = 1,
                    scheduledAtEpochMillis = 1_000,
                    startedAtEpochMillis = 1_100,
                ),
            )
            occurrenceDao.recordDismissal("account-1", "execution-1", 1_200)
            occurrenceDao.recordTerminal("account-1", "execution-1", AlarmOccurrenceOutboxKind.ARRIVED, 1_300)
            occurrenceDao.recordIosStart(
                decision = IosAlarmOccurrenceRecordEntity(
                    chainId = "ios-chain-1",
                    rootOccurrenceId = "ios-execution-1",
                    state = IosAlarmOccurrenceRecordState.AUTH_PENDING,
                    ownerAccountId = "account-2",
                    alarmId = "alarm-1",
                    scheduleVersion = 1,
                    scheduledAtEpochMillis = 2_000,
                    startedAtEpochMillis = 2_100,
                ),
                execution = AlarmOccurrenceSyncEntity(
                    localExecutionId = "ios-execution-1",
                    ownerAccountId = "account-2",
                    alarmId = "alarm-1",
                    scheduleVersion = 1,
                    scheduledAtEpochMillis = 2_000,
                    startedAtEpochMillis = 2_100,
                ),
            )

            val ringingEvent = AlarmActivityEntity.rang(
                alarmId = "alarm-1",
                occurrenceId = "execution-1",
                timestamp = AlarmActivityTimestamp(1_100, "2026-11-09"),
            )
            activityDao.recordObservedRinging(mapOf("system-alarm-1" to ringingEvent))
            missionHistoryDao.insert(
                MissionHistoryEntity(
                    result = "SUCCESS",
                    completedAt = "2026-11-09",
                    occurrenceId = "execution-1",
                ),
            )

            val cleanup = RoomLocalUserDataCleanup(database.localUserDataCleanupDao())
            cleanup.clear()
            cleanup.clear()

            assertTrue(alarmDao.getAll().isEmpty())
            assertTrue(destinationDao.observeAll(ownerAccountId = null).first().isEmpty())
            assertTrue(destinationDao.observeOutbox().first().isEmpty())
            assertNull(destinationDao.getSyncState("account-1"))
            assertEquals(0, activityDao.observeCounts("2026-11-09").first().ringingCount)
            assertNull(activityDao.observeCounts("2026-11-09").first().trackingStartDate)
            assertNull(activityDao.getOccurrenceTimes("execution-1"))
            assertTrue(activityDao.getObservations().isEmpty())
            assertTrue(missionHistoryDao.getHistory("2026-11-01", "2026-11-30").isEmpty())
            assertNull(arrivalDao.getResult("execution-1"))
            assertEquals(0, arrivalDao.getSuccessfulDayCount())
            assertEquals(growthActivation, arrivalDao.getGrowthActivation())
            assertTrue(occurrenceDao.getEvents("account-1", "execution-1").isEmpty())
            assertTrue(occurrenceDao.getMovements("account-1", "execution-1").isEmpty())
            assertNull(occurrenceDao.getExecution("account-1", "execution-1"))
            assertNull(occurrenceDao.getIosRecordDecision("ios-chain-1"))
            assertNull(occurrenceDao.getExecution("account-2", "ios-execution-1"))
            assertTrue(alarmDao.hasStorageMigration("legacy-import"))
            database.useReaderConnection { connection ->
                connection.usePrepared("SELECT COUNT(*) FROM alarm_repeat_days") { statement ->
                    assertTrue(statement.step())
                    assertEquals(0L, statement.getLong(0))
                }
                connection.usePrepared("PRAGMA foreign_key_check") { statement ->
                    assertFalse(statement.step())
                }
            }

            // Deleting the parent succeeds with foreign keys enabled because repeat days cascade.
        } finally {
            database.close()
        }
    }

    private fun sampleAlarm() = AlarmEntity(
        id = "alarm-1",
        time = "07:05",
        repeatEnabled = true,
        limitMinutes = 10,
        destinationLabel = "회사",
        destinationLatitude = 37.5,
        destinationLongitude = 127.0,
        targetDistanceKm = 1.0,
        alarmSoundName = "기본 알람음",
        alarmSoundUri = null,
        enabled = true,
    )
}
