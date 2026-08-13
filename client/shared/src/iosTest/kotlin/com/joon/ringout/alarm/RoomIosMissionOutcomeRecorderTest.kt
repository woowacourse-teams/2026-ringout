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
    fun recordsEachOccurrenceOnceAndPersistsSuccessStampsAcrossDatabaseReopen() = runBlocking {
        val databasePath = NSTemporaryDirectory() +
            "ringout-outcome-${NSUUID().UUIDString}.db"
        try {
            val firstDatabase = openDatabase(databasePath)
            val firstRepository = repository(firstDatabase)
            val recorder = RoomIosMissionOutcomeRecorder(RecordMissionResult(firstRepository))

            recorder.recordSuccess("success-occurrence", "2026-08-05")
            recorder.recordFailure("success-occurrence", "2026-08-05")
            recorder.recordFailure("same-day-failure", "2026-08-05")
            recorder.recordFailure("failure-only", "2026-08-06")
            firstDatabase.close()

            val restoredDatabase = openDatabase(databasePath)
            try {
                val restoredRepository = repository(restoredDatabase)
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
