package com.joon.ringout.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.missionhistory.MissionHistoryEntity
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RingoutDatabaseTest {
    @Test
    fun storesAndReadsMissionHistoryForRequestedMonth() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<RingoutDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()

        try {
            val dao = database.missionHistoryDao()
            dao.insert(MissionHistoryEntity(result = "SUCCESS", completedAt = "2026-08-05"))
            dao.insert(MissionHistoryEntity(result = "FAILURE", completedAt = "2026-08-06"))
            dao.insert(MissionHistoryEntity(result = "SUCCESS", completedAt = "2026-09-01"))

            assertEquals(
                listOf("2026-08-05", "2026-08-06"),
                RoomMissionHistoryDataSource(dao)
                    .getHistory(MissionYearMonth(2026, 8))
                    .map { it.completedAt },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun ignoresDuplicateOccurrenceButAllowsDifferentOccurrencesOnTheSameDate() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<RingoutDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()

        try {
            val dao = database.missionHistoryDao()
            assertTrue(
                dao.insert(
                    MissionHistoryEntity(
                        result = "SUCCESS",
                        completedAt = "2026-08-05",
                        occurrenceId = "occurrence-1",
                    ),
                ),
            )
            assertFalse(
                dao.insert(
                    MissionHistoryEntity(
                        result = "FAILURE",
                        completedAt = "2026-08-05",
                        occurrenceId = "occurrence-1",
                    ),
                ),
            )
            assertTrue(
                dao.insert(
                    MissionHistoryEntity(
                        result = "FAILURE",
                        completedAt = "2026-08-05",
                        occurrenceId = "occurrence-2",
                    ),
                ),
            )
            assertEquals(
                listOf("occurrence-1", "occurrence-2"),
                dao.getHistory("2026-08-01", "2026-08-31").map { it.occurrenceId },
            )
        } finally {
            database.close()
        }
    }
}
