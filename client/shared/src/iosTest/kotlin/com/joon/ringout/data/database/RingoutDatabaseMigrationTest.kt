@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.destination.DefaultDestinationRepository
import com.joon.ringout.data.destination.RoomDestinationDataSource
import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RingoutDatabaseMigrationTest {
    @Test
    fun `버전 육의 울림 이벤트에서 시작 시각만 복원하고 없는 종료와 완료 시각은 비워 둔다`() = runBlocking<Unit> {
        val path = temporaryDatabasePath()
        createVersionFiveDatabase(path)
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execSQL("DELETE FROM alarm_activity_events WHERE type = 'CREATED'")
            connection.execSQL("UPDATE mission_history SET occurrence_id = 'one', completed_at = '2026-09-28'")
            connection.execSQL("PRAGMA user_version = 6")
        }
        val database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val entry = database.missionHistoryDao().getHistoryWithTimes("2026-09-01", "2026-09-30").single()
            assertEquals("one", entry.history.occurrenceId)
            assertEquals(2_000L, entry.ringingStartedAtEpochMillis)
            kotlin.test.assertNull(entry.ringingStoppedAtEpochMillis)
            kotlin.test.assertNull(entry.missionCompletedAtEpochMillis)
            assertEquals(true, entry.isRingingStartObserved)
            assertEquals(1, database.alarmActivityDao().observeCounts("2026-09-28").first().ringingCount)
            assertNotNull(database.alarmDao().getById("alarm-v2"))
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    @Test
    fun `버전 오에서 생성 집계 이벤트만 제거하고 울림과 기존 알람을 보존한다`() = runBlocking {
        val path = temporaryDatabasePath()
        createVersionFiveDatabase(path)
        val database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val counts = database.alarmActivityDao().observeCounts("2026-09-28").first()
            assertEquals(1, counts.ringingCount)
            assertEquals("2026-09-28", counts.trackingStartDate)
            assertNotNull(database.alarmDao().getById("alarm-v2"))
            assertEquals(1, database.missionHistoryDao().getHistory("2026-08-01", "2026-08-31").size)
            assertEquals("rang:one", database.alarmActivityDao().getObservations().single().eventKey)
            BundledSQLiteDriver().open(path).use { connection ->
                connection.prepare("SELECT event_key FROM alarm_activity_events ORDER BY event_key").use { statement ->
                    val keys = buildList {
                        while (statement.step()) add(statement.getText(0))
                    }
                    assertEquals(listOf("rang:one"), keys)
                }
            }
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    @Test
    fun `버전 사에서 기존 알람과 미션을 보존하고 활동 집계 테이블을 추가한다`() = runBlocking {
        val path = temporaryDatabasePath()
        createVersionThreeDatabase(path)
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execSQL("ALTER TABLE mission_history ADD COLUMN occurrence_id TEXT")
            connection.execSQL("CREATE UNIQUE INDEX index_mission_history_occurrence_id ON mission_history (occurrence_id)")
            connection.execSQL("PRAGMA user_version = 4")
        }
        val database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            assertNotNull(database.alarmDao().getById("alarm-v2"))
            assertEquals(1, database.missionHistoryDao().getHistory("2026-08-01", "2026-08-31").size)
            val counts = database.alarmActivityDao().observeCounts("2026-08-05").first()
            assertEquals(0, counts.ringingCount)
            kotlin.test.assertNull(counts.trackingStartDate)
            database.alarmActivityDao().record(
                com.joon.ringout.data.alarmactivity.AlarmActivityEntity.rang(
                    "alarm-v2", "new-ring", com.joon.ringout.data.alarmactivity.AlarmActivityTimestamp(1_000, "2026-09-28"),
                ),
            )
            assertEquals(1, database.alarmActivityDao().observeCounts("2026-09-28").first().ringingCount)
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    @Test
    fun migratesVersionOneToCurrentWithoutLosingMissionHistory() = runBlocking {
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

            val destinationRepository = destinationRepository(database)
            val savedDestination = destinationRepository.save(destination(name = "회사"))
            assertTrue(savedDestination.id > 0L)
            assertEquals(listOf(savedDestination), destinationRepository.observeAll().first())
        } finally {
            database.close()
            deleteDatabaseFiles(databasePath)
        }
    }

    @Test
    fun migratesVersionTwoWithoutLosingExistingTablesAndCreatesSavedDestinations() = runBlocking {
        val databasePath = temporaryDatabasePath()
        createVersionTwoDatabase(databasePath)

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
            val migratedAlarm = assertNotNull(alarmDataSource.getById("alarm-v2"))
            assertEquals("07:05", migratedAlarm.request.time)
            assertEquals(listOf("월", "금"), migratedAlarm.request.selectedDays)
            assertTrue(alarmDataSource.hasStorageMigration("legacy-alarm-import"))

            val destinationRepository = destinationRepository(database)
            assertTrue(destinationRepository.observeAll().first().isEmpty())
            val savedDestination = destinationRepository.save(
                destination(name = "집", address = ""),
            )
            assertEquals(listOf(savedDestination), destinationRepository.observeAll().first())
        } finally {
            database.close()
            deleteDatabaseFiles(databasePath)
        }
    }

    @Test
    fun migratesVersionThreeByKeepingLegacyRowsAndAddingOccurrenceUniqueness() = runBlocking {
        val databasePath = temporaryDatabasePath()
        createVersionThreeDatabase(databasePath)

        val database = buildRingoutDatabase(
            Room.databaseBuilder<RingoutDatabase>(name = databasePath),
        )
        try {
            val dao = database.missionHistoryDao()
            assertEquals(
                listOf(null),
                dao.getHistory("2026-08-01", "2026-08-31").map { it.occurrenceId },
            )
            assertTrue(
                dao.insert(
                    com.joon.ringout.data.missionhistory.MissionHistoryEntity(
                        result = "SUCCESS",
                        completedAt = "2026-08-05",
                        occurrenceId = "occurrence-1",
                    ),
                ),
            )
            assertEquals(
                false,
                dao.insert(
                    com.joon.ringout.data.missionhistory.MissionHistoryEntity(
                        result = "FAILURE",
                        completedAt = "2026-08-05",
                        occurrenceId = "occurrence-1",
                    ),
                ),
            )
        } finally {
            database.close()
            deleteDatabaseFiles(databasePath)
        }
    }

    private fun createVersionFiveDatabase(databasePath: String) {
        createVersionThreeDatabase(databasePath)
        BundledSQLiteDriver().open(databasePath).use { connection ->
            connection.execSQL(
                "ALTER TABLE mission_history ADD COLUMN occurrence_id TEXT",
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX index_mission_history_occurrence_id ON mission_history (occurrence_id)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `alarm_activity_events` (`event_key` TEXT NOT NULL, `alarm_id` TEXT NOT NULL, `type` TEXT NOT NULL, `occurred_at_epoch_millis` INTEGER NOT NULL, `local_date` TEXT NOT NULL, PRIMARY KEY(`event_key`))",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_alarm_activity_events_local_date_type` ON `alarm_activity_events` (`local_date`, `type`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `alarm_activity_tracking` (`id` INTEGER NOT NULL, `started_at_epoch_millis` INTEGER NOT NULL, `local_date` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `alarm_ringing_observations` (`system_alarm_id` TEXT NOT NULL, `event_key` TEXT NOT NULL, PRIMARY KEY(`system_alarm_id`))",
            )
            connection.execSQL(
                "INSERT INTO alarm_activity_events VALUES ('created:alarm-v2', 'alarm-v2', 'CREATED', 1000, '2026-09-28')",
            )
            connection.execSQL(
                "INSERT INTO alarm_activity_events VALUES ('rang:one', 'alarm-v2', 'RANG', 2000, '2026-09-28')",
            )
            connection.execSQL(
                "INSERT INTO alarm_activity_tracking VALUES (1, 1000, '2026-09-28')",
            )
            connection.execSQL(
                "INSERT INTO alarm_ringing_observations VALUES ('system-alarm', 'rang:one')",
            )
            connection.execSQL(
                "PRAGMA user_version = 5",
            )
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

    private fun createVersionTwoDatabase(databasePath: String) {
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
                CREATE TABLE IF NOT EXISTS `alarms` (
                    `id` TEXT NOT NULL,
                    `time` TEXT NOT NULL,
                    `repeat_enabled` INTEGER NOT NULL,
                    `limit_minutes` INTEGER NOT NULL,
                    `destination_name` TEXT NOT NULL,
                    `destination_address` TEXT NOT NULL,
                    `destination_latitude` REAL NOT NULL,
                    `destination_longitude` REAL NOT NULL,
                    `target_distance_km` REAL NOT NULL,
                    `alarm_sound_name` TEXT NOT NULL,
                    `alarm_sound_uri` TEXT,
                    `enabled` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_alarms_enabled` ON `alarms` (`enabled`)",
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `alarm_repeat_days` (
                    `alarm_id` TEXT NOT NULL,
                    `day_of_week` INTEGER NOT NULL,
                    PRIMARY KEY(`alarm_id`, `day_of_week`),
                    FOREIGN KEY(`alarm_id`) REFERENCES `alarms`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `storage_migrations` (
                    `id` TEXT NOT NULL,
                    `completed_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `mission_history` (`result`, `completed_at`)
                VALUES ('SUCCESS', '2026-08-05')
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `alarms` (
                    `id`, `time`, `repeat_enabled`, `limit_minutes`,
                    `destination_name`, `destination_address`,
                    `destination_latitude`, `destination_longitude`,
                    `target_distance_km`, `alarm_sound_name`, `alarm_sound_uri`, `enabled`
                ) VALUES (
                    'alarm-v2', '07:05', 1, 12,
                    '회사', '서울특별시 중구 세종대로 110',
                    37.5665, 126.978,
                    1.2, '기본 알람음', NULL, 1
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `alarm_repeat_days` (`alarm_id`, `day_of_week`)
                VALUES ('alarm-v2', 1), ('alarm-v2', 5)
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO `storage_migrations` (`id`, `completed_at_epoch_millis`)
                VALUES ('legacy-alarm-import', 1000)
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            connection.execSQL(
                """
                INSERT OR REPLACE INTO room_master_table (id, identity_hash)
                VALUES(42, '6e426c4d6f4abdbbcc04fa0759c6d3fa')
                """.trimIndent(),
            )
            connection.execSQL("PRAGMA user_version = 2")
        }
    }

    private fun createVersionThreeDatabase(databasePath: String) {
        createVersionTwoDatabase(databasePath)
        BundledSQLiteDriver().open(databasePath).use { connection ->
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `saved_destinations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `address` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT OR REPLACE INTO room_master_table (id, identity_hash)
                VALUES(42, 'd8699d58e420e62b054ec9c812b191ac')
                """.trimIndent(),
            )
            connection.execSQL("PRAGMA user_version = 3")
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

    private fun destinationRepository(database: RingoutDatabase) =
        DefaultDestinationRepository(RoomDestinationDataSource(database.destinationDao()))

    private fun destination(
        name: String,
        address: String = "서울특별시 중구 세종대로 110",
    ) = SavedDestination(
        name = name,
        address = address,
        latitude = 37.5665,
        longitude = 126.978,
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
