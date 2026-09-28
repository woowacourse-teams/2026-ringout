@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.alarm

import androidx.room3.Room
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.RecordMissionResult
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomIosMissionOutcomeRecorderTest {
    @Test
    fun `실행별 시각과 성공 기록을 중복 없이 보존하고 데이터베이스를 다시 열어도 유지한다`() = runBlocking {
        val databasePath = NSTemporaryDirectory() +
            "ringout-outcome-${NSUUID().UUIDString}.db"
        try {
            val firstDatabase = openDatabase(databasePath)
            val firstRepository = repository(firstDatabase)
            val recorder = RoomIosMissionOutcomeRecorder(RecordMissionResult(firstRepository), firstDatabase.alarmActivityDao())
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
