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
import com.joon.ringout.data.alarmoccurrence.IosAlarmOccurrenceRecordEntity
import com.joon.ringout.data.alarmoccurrence.IosAlarmOccurrenceRecordState
import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RingoutDatabaseMigrationTest {
    @Test
    fun `버전 십이에서 label을 보정하고 알람과 연관 저장 데이터를 보존한다`() = runBlocking {
        val path = temporaryDatabasePath()
        var database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val destinationRepo = destinationRepository(database)
            destinationRepo.save(destination(label = "회사", latitude = 37.5, longitude = 127.0))
            destinationRepo.save(destination(label = "임시", latitude = 37.6, longitude = 127.1))
            destinationRepo.save(destination(label = "임시", latitude = 37.7, longitude = 127.2))

            val alarmDataSource = RoomAlarmDataSource(database.alarmDao())
            val originalRequest = savedAlarm().request.copy(scheduleVersion = 7)
            alarmDataSource.replace(SavedAlarmSchedule(originalRequest, enabled = true))
            alarmDataSource.replace(
                SavedAlarmSchedule(
                    originalRequest.copy(id = "alarm-address-fallback", destinationLabel = "임시"),
                    enabled = false,
                ),
            )
            alarmDataSource.replace(
                SavedAlarmSchedule(
                    originalRequest.copy(id = "alarm-empty-fallback", destinationLabel = "임시"),
                    enabled = true,
                ),
            )
            database.missionHistoryDao().insert(
                com.joon.ringout.data.missionhistory.MissionHistoryEntity(
                    result = "SUCCESS",
                    completedAt = "2026-10-01",
                    occurrenceId = "migration-occurrence",
                ),
            )
            database.alarmActivityDao().record(
                com.joon.ringout.data.alarmactivity.AlarmActivityEntity.rang(
                    alarmId = "alarm-1",
                    occurrenceId = "migration-occurrence",
                    timestamp = com.joon.ringout.data.alarmactivity.AlarmActivityTimestamp(
                        1_000,
                        "2026-10-01",
                    ),
                    scheduleVersion = 7,
                ),
            )
            val sync = database.alarmOccurrenceSyncDao()
            sync.recordStart(
                com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncEntity(
                    "migration-execution",
                    "migration-account",
                    "alarm-1",
                    7,
                    1_000,
                    1_010,
                ),
            )
            sync.recordDismissal("migration-account", "migration-execution", 2_000)
            database.close()

            BundledSQLiteDriver().open(path).use { connection ->
                connection.execSQL("ALTER TABLE saved_destinations ADD COLUMN address TEXT NOT NULL DEFAULT ''")
                connection.execSQL("ALTER TABLE saved_destinations RENAME COLUMN label TO name")
                connection.execSQL("UPDATE saved_destinations SET name = char(9, 32), address = ' 서울 주소 ' WHERE id = 2")
                connection.execSQL("UPDATE saved_destinations SET name = char(9, 32), address = char(9, 32) WHERE id = 3")
                connection.execSQL("ALTER TABLE alarms ADD COLUMN destination_address TEXT NOT NULL DEFAULT ''")
                connection.execSQL("ALTER TABLE alarms RENAME COLUMN destination_label TO destination_name")
                connection.execSQL("UPDATE alarms SET destination_address = '서울특별시 중구 세종대로 110' WHERE id = 'alarm-1'")
                connection.execSQL("UPDATE alarms SET destination_name = char(9, 32), destination_address = '부산 주소' WHERE id = 'alarm-address-fallback'")
                connection.execSQL("UPDATE alarms SET destination_name = char(9, 32), destination_address = char(9, 32) WHERE id = 'alarm-empty-fallback'")
                connection.execSQL("UPDATE room_master_table SET identity_hash = '7f803ac5d546788e54963c61d86ea315' WHERE id = 42")
                connection.execSQL("PRAGMA user_version = 12")
            }

            database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
            val migratedDestinations = destinationRepository(database).observeAll().first()
            assertEquals(listOf(1L, 2L, 3L), migratedDestinations.map(SavedDestination::id))
            assertEquals(listOf("회사", " 서울 주소 ", "주소 정보 없음"), migratedDestinations.map(SavedDestination::label))
            assertEquals(listOf(37.5, 37.6, 37.7), migratedDestinations.map(SavedDestination::latitude))

            val migratedAlarms = RoomAlarmDataSource(database.alarmDao()).getAll().associateBy { it.request.id }
            assertEquals("회사", migratedAlarms.getValue("alarm-1").request.destinationLabel)
            assertEquals("부산 주소", migratedAlarms.getValue("alarm-address-fallback").request.destinationLabel)
            assertEquals("주소 정보 없음", migratedAlarms.getValue("alarm-empty-fallback").request.destinationLabel)
            assertEquals(listOf("월", "금"), migratedAlarms.getValue("alarm-1").request.selectedDays)
            assertEquals(7L, migratedAlarms.getValue("alarm-1").request.scheduleVersion)
            assertTrue(migratedAlarms.getValue("alarm-1").enabled)
            assertTrue(!migratedAlarms.getValue("alarm-address-fallback").enabled)
            assertEquals(37.5665, migratedAlarms.getValue("alarm-1").request.destinationLatitude)
            assertEquals("기본 알람음", migratedAlarms.getValue("alarm-1").request.alarmSoundName)

            assertEquals(1, database.missionHistoryDao().getHistory("2026-10-01", "2026-10-31").size)
            assertEquals(1, database.alarmActivityDao().observeCounts("2026-10-01").first().ringingCount)
            assertEquals(2, database.alarmOccurrenceSyncDao().getUnsentEvents("migration-account").size)
            BundledSQLiteDriver().open(path).use { connection ->
                val savedDestinationColumns = columnNames(connection, "saved_destinations")
                val alarmColumns = columnNames(connection, "alarms")
                assertTrue("label" in savedDestinationColumns)
                assertTrue("name" !in savedDestinationColumns && "address" !in savedDestinationColumns)
                assertTrue("destination_label" in alarmColumns)
                assertTrue("destination_name" !in alarmColumns && "destination_address" !in alarmColumns)
                assertTrue("index_alarms_enabled" in indexNames(connection, "alarms"))
                connection.prepare("PRAGMA foreign_key_check").use { statement ->
                    assertFalse(statement.step())
                }
            }
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    @Test
    fun `버전 십일에서 iOS 기록 판단 테이블을 추가하고 로컬 전용 결정을 보존한다`() = runBlocking {
        val path = temporaryDatabasePath()
        var database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            database.alarmOccurrenceSyncDao().getPendingIosRecordDecisions()
            database.close()
            BundledSQLiteDriver().open(path).use { connection ->
                connection.execSQL("DROP TABLE ios_alarm_occurrence_recording")
                connection.execSQL("UPDATE room_master_table SET identity_hash = '48a8b44654750f7b165ea3a10ff4ffb9' WHERE id = 42")
                connection.execSQL("PRAGMA user_version = 11")
            }
            database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
            val dao = database.alarmOccurrenceSyncDao()
            dao.keepIosRecordLocalOnly(IosAlarmOccurrenceRecordEntity(
                chainId = "local-chain",
                rootOccurrenceId = "local-root",
                state = IosAlarmOccurrenceRecordState.AUTH_PENDING,
                ownerAccountId = null,
                alarmId = "alarm",
                scheduleVersion = 1,
                scheduledAtEpochMillis = 1_000,
                startedAtEpochMillis = 1_000,
            ))
            assertEquals(IosAlarmOccurrenceRecordState.LOCAL_ONLY, dao.getIosRecordDecision("local-chain")?.state)
            assertTrue(dao.getPendingIosRecordDecisions().isEmpty())
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    @Test
    fun `버전 십의 미전송 알람을 보존하고 과거 이동 이벤트를 임의 생성하지 않는다`() = runBlocking {
        val path = temporaryDatabasePath()
        var database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val sync = database.alarmOccurrenceSyncDao()
            sync.recordStart(com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncEntity(
                "existing", "1", "alarm", 1, 1_000, 1_001,
            ))
            sync.recordDismissal("1", "existing", 2_000)
            database.close()
            // 10과 11의 기존 테이블은 동일하다. 새 테이블을 제거해 10의 저장 파일로 복원한다.
            BundledSQLiteDriver().open(path).use { connection ->
                connection.execSQL("DROP TABLE alarm_movement_outbox")
                connection.execSQL("UPDATE room_master_table SET identity_hash = '91e792c227f600707f21ca662a258bb9' WHERE id = 42")
                connection.execSQL("PRAGMA user_version = 10")
            }
            database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
            val migrated = database.alarmOccurrenceSyncDao()
            assertEquals(2, migrated.getUnsentEvents("1").size)
            assertEquals(2_000L, migrated.getEvents("1", "existing").last().occurredAtEpochMillis)
            assertTrue(migrated.getUnsentMovements("1").isEmpty())
            migrated.recordTerminal("1", "existing", com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxKind.ARRIVED, 3_000)
            assertEquals(com.joon.ringout.domain.alarmmovement.AlarmMovementAction.ARRIVE,
                migrated.getUnsentMovements("1").single().action)
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    @Test
    fun `버전 구의 기존 데이터를 보존하고 빈 알람 전송 저장소를 추가한다`() = runBlocking<Unit> {
        val path = temporaryDatabasePath()
        createVersionFiveDatabase(path)
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execSQL("DELETE FROM alarm_activity_events WHERE type = 'CREATED'")
            connection.execSQL("""
                CREATE TABLE alarm_occurrence_times (
                    occurrence_id TEXT NOT NULL PRIMARY KEY,
                    ringing_started_at INTEGER, ringing_stopped_at INTEGER, mission_completed_at INTEGER,
                    ringing_start_observed INTEGER NOT NULL, ringing_scheduled_at INTEGER
                )
            """.trimIndent())
            connection.execSQL("INSERT INTO alarm_occurrence_times VALUES ('one', 2000, 3000, 4000, 1, 1000)")
            connection.execSQL("ALTER TABLE alarms ADD COLUMN schedule_version INTEGER NOT NULL DEFAULT 1")
            connection.execSQL("ALTER TABLE alarm_activity_events ADD COLUMN schedule_version INTEGER NOT NULL DEFAULT 1")
            connection.execSQL("INSERT INTO saved_destinations (name, address, latitude, longitude) VALUES ('회사', '서울', 37.5, 127.0)")
            connection.execSQL("PRAGMA user_version = 9")
        }
        val database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            assertNotNull(database.alarmDao().getById("alarm-v2"))
            assertEquals(1, database.missionHistoryDao().getHistory("2026-08-01", "2026-08-31").size)
            assertEquals(1, database.alarmActivityDao().observeCounts("2026-09-28").first().ringingCount)
            assertEquals("rang:one", database.alarmActivityDao().getObservations().single().eventKey)
            assertEquals(1_000L, database.alarmActivityDao().getOccurrenceTimes("one")?.ringingScheduledAtEpochMillis)
            assertEquals(4_000L, database.alarmActivityDao().getOccurrenceTimes("one")?.missionCompletedAtEpochMillis)
            assertEquals("회사", destinationRepository(database).observeAll().first().single().label)
            val sync = database.alarmOccurrenceSyncDao()
            assertTrue(sync.getUnsentEvents("account-1").isEmpty())
            sync.recordStart(com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncEntity(
                "new-execution", "account-1", "alarm-v2", 1, 5_000, 5_010,
            ))
            sync.recordDismissal("account-1", "new-execution", 6_000)
            database.alarmDao().delete("alarm-v2")
            assertEquals(2, sync.getUnsentEvents("account-1").size)
            assertNotNull(database.alarmActivityDao().getOccurrenceTimes("one"))
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    @Test
    fun `버전 칠의 실행 시각을 보존하고 예정 시각은 추정하지 않은 채 새 열을 추가한다`() = runBlocking {
        val path = temporaryDatabasePath()
        createVersionFiveDatabase(path)
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execSQL("DELETE FROM alarm_activity_events WHERE type = 'CREATED'")
            connection.execSQL("""
                CREATE TABLE alarm_occurrence_times (
                    occurrence_id TEXT NOT NULL PRIMARY KEY,
                    ringing_started_at INTEGER, ringing_stopped_at INTEGER, mission_completed_at INTEGER,
                    ringing_start_observed INTEGER NOT NULL
                )
            """.trimIndent())
            connection.execSQL("INSERT INTO alarm_occurrence_times VALUES ('one', 2000, 3000, 4000, 1)")
            connection.execSQL("PRAGMA user_version = 7")
        }
        val database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val times = assertNotNull(database.alarmActivityDao().getOccurrenceTimes("one"))
            assertEquals(2_000L, times.ringingStartedAtEpochMillis)
            assertEquals(3_000L, times.ringingStoppedAtEpochMillis)
            assertEquals(4_000L, times.missionCompletedAtEpochMillis)
            kotlin.test.assertNull(times.ringingScheduledAtEpochMillis)
            database.alarmActivityDao().mergeOccurrenceTimes(times.copy(ringingScheduledAtEpochMillis = 1_000))
            database.alarmActivityDao().mergeOccurrenceTimes(times.copy(ringingScheduledAtEpochMillis = 9_000))
            assertEquals(1_000L, database.alarmActivityDao().getOccurrenceTimes("one")?.ringingScheduledAtEpochMillis)
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

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
            val savedDestination = destinationRepository.save(destination(label = "회사"))
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
                destination(label = "집"),
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
            destinationLabel = "회사",
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
        label: String,
        latitude: Double = 37.5665,
        longitude: Double = 126.978,
    ) = SavedDestination(
        label = label,
        latitude = latitude,
        longitude = longitude,
    )

    private fun columnNames(
        connection: androidx.sqlite.SQLiteConnection,
        tableName: String,
    ): Set<String> = connection.prepare("PRAGMA table_info(`$tableName`)").use { statement ->
        buildSet {
            while (statement.step()) add(statement.getText(1))
        }
    }

    private fun indexNames(
        connection: androidx.sqlite.SQLiteConnection,
        tableName: String,
    ): Set<String> = connection.prepare("PRAGMA index_list(`$tableName`)").use { statement ->
        buildSet {
            while (statement.step()) add(statement.getText(1))
        }
    }

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
