@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RingoutDatabaseMigrationTest {
    @Test
    fun migratesVersionOneWithoutLosingMissionHistory() = runBlocking {
        val databasePath = temporaryDatabasePath()
        createVersionOneDatabase(databasePath)

        val database = buildRingoutDatabase(
            Room.databaseBuilder<RingoutDatabase>(name = databasePath),
        )
        try {
            assertEquals(
                listOf("2026-08-05"),
                database.missionHistoryDao()
                    .getHistory("2026-08-01", "2026-08-31")
                    .map { it.completedAt },
            )

            val alarmDataSource = RoomAlarmDataSource(database.alarmDao())
            alarmDataSource.replace(savedAlarm())
            assertEquals(
                listOf("월", "금"),
                assertNotNull(alarmDataSource.getById("alarm-1")).request.selectedDays,
            )
        } finally {
            database.close()
            deleteDatabaseFiles(databasePath)
        }
    }

    private fun createVersionOneDatabase(databasePath: String) {
        BundledSQLiteDriver().open(databasePath).use { connection ->
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `mission_history` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `result` TEXT NOT NULL,
                    `completed_at` TEXT NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_mission_history_completed_at`
                ON `mission_history` (`completed_at`)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `mission_history` (`result`, `completed_at`)
                VALUES ('SUCCESS', '2026-08-05')
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            connection.execSQL(
                """
                INSERT OR REPLACE INTO room_master_table (id, identity_hash)
                VALUES(42, 'e139ef635bd2dd9b812e3268acaf1dc9')
                """.trimIndent(),
            )
            connection.execSQL("PRAGMA user_version = 1")
        }
    }

    private fun savedAlarm() = SavedAlarmSchedule(
        request = AlarmScheduleRequest(
            id = "alarm-1",
            time = "07:05",
            selectedDays = listOf("월", "금"),
            repeatEnabled = true,
            limitMinutes = 12,
            destinationName = "회사",
            destinationAddress = "서울특별시 중구 세종대로 110",
            destinationLatitude = 37.5665,
            destinationLongitude = 126.978,
            targetDistanceKm = 1.2,
            alarmSoundName = "기본 알람음",
            alarmSoundUri = null,
        ),
        enabled = true,
    )

    private fun temporaryDatabasePath(): String =
        NSTemporaryDirectory() + "ringout-migration-${NSUUID().UUIDString}.db"

    private fun deleteDatabaseFiles(databasePath: String) {
        listOf(databasePath, "$databasePath-shm", "$databasePath-wal").forEach { path ->
            if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
                NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            }
        }
    }
}
