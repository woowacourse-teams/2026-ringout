package com.joon.ringout.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.missionhistory.MissionHistoryEntity
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
