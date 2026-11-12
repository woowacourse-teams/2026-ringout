package com.joon.ringout.data.database

import androidx.room3.Room
import androidx.sqlite.execSQL
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.alarmactivity.AlarmActivityEntity
import com.joon.ringout.data.alarmactivity.AlarmActivityTimestamp
import com.joon.ringout.data.alarmactivity.AlarmOccurrenceTimesEntity
import com.joon.ringout.data.arrival.RoomArrivalResultRepository
import com.joon.ringout.data.destination.DefaultDestinationRepository
import com.joon.ringout.data.destination.DestinationAccountProvider
import com.joon.ringout.data.destination.DestinationOutboxSyncer
import com.joon.ringout.data.destination.DestinationOutboxOperation
import com.joon.ringout.data.destination.DestinationRemoteDataSource
import com.joon.ringout.data.destination.DestinationSyncSessionGate
import com.joon.ringout.data.destination.RemoteSavedDestination
import com.joon.ringout.data.destination.RoomDestinationDataSource
import com.joon.ringout.data.alarmoccurrence.IosAlarmOccurrenceRecordEntity
import com.joon.ringout.data.alarmoccurrence.IosAlarmOccurrenceRecordState
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxKind
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncEntity
import com.joon.ringout.data.alarmoccurrence.CachedAlarmOccurrenceEntity
import com.joon.ringout.data.alarmoccurrence.CachedAlarmRingingEntity
import com.joon.ringout.data.missionhistory.MissionHistoryEntity
import com.joon.ringout.data.missionhistory.MissionHistoryDto
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RingoutDatabaseMigrationTestSuite {
    fun `버전 십팔의 알람은 기존 서버 ID와 요일을 보존하고 기기 예약 ID를 따로 저장한다`() = runBlocking {
        val path = temporaryDatabasePath()
        val builder = { buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path)) }
        try {
            val original = savedAlarm().let { it.copy(request = it.request.copy(ownerAccountId = "account-1")) }
            builder().useDatabase { RoomAlarmDataSource(it).replace(original) }
            createMigrationTestSQLiteDriver().open(path).use { connection ->
                setDatabaseVersion(connection, 18, "f1f45244a66bb260740b78fbe0e3d855")
            }

            val pending = builder().useDatabase { database ->
                val restored = assertNotNull(RoomAlarmDataSource(database).getById(original.request.id))
                assertEquals(original.request.id, restored.request.remoteAlarmId)
                assertEquals(original.request.selectedDays, restored.request.selectedDays)
                assertTrue(restored.enabled)
                assertNotNull(restored.request.pendingReservationId)
            }
            builder().useDatabase { database ->
                val source = RoomAlarmDataSource(database)
                val stored = assertNotNull(source.getById(original.request.id))
                assertEquals(pending, stored.request.pendingReservationId)
                assertTrue(source.migrateId(stored.request.id, pending))
                assertNull(source.getById(stored.request.id))
                val migrated = assertNotNull(source.getById(pending))
                assertEquals(original.request.id, migrated.request.remoteAlarmId)
                assertNull(migrated.request.pendingReservationId)
                assertEquals(original.request.selectedDays, migrated.request.selectedDays)
                assertTrue(migrated.enabled)
            }
            assertDatabaseVersion(path, expectedVersion = 19)
        } finally {
            deleteDatabaseFiles(path)
        }
    }

    fun `알람 변경과 삭제는 순서대로 남고 기기 설정만 바꾸면 전송하지 않는다`() = runBlocking {
        val path = temporaryDatabasePath()
        val builder = { buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path)) }
        try {
            builder().useDatabase { database ->
                val source = RoomAlarmDataSource(database)
                val draft = savedAlarm().let {
                    it.copy(request = it.request.copy(ownerAccountId = "account-1", remoteAlarmId = "server-id"))
                }
                source.replaceUserAlarm(draft, previous = null)
                val changed = draft.copy(request = draft.request.copy(time = "08:30"))
                source.replaceUserAlarm(changed, previous = draft)
                source.replaceUserAlarm(changed.copy(request = changed.request.copy(alarmSoundName = "다른 소리")), changed)
                source.setEnabled(changed.request.id, false)
                assertTrue(source.deleteUserAlarm(changed.request.id))
            }
            builder().useDatabase { database ->
                val queued = database.alarmSyncDao().getOutbox("account-1")
                assertEquals(listOf("server-id", "server-id", "server-id"), queued.map { it.remoteAlarmId })
                val puts = queued.take(2).map {
                    kotlinx.serialization.json.Json.decodeFromString<com.joon.ringout.data.alarm.RemoteAlarm>(
                        assertNotNull(it.payload),
                    )
                }
                assertEquals(listOf(1L, 2L), puts.map { it.alarmVersion })
                assertEquals(listOf("07:05", "08:30"), puts.map { it.time })
                assertNull(queued.last().payload)
                assertTrue(database.alarmDao().getAll().isEmpty())
                database.alarmSyncDao().setRestoreComplete("account-1")
                database.localUserDataCleanupDao().clear()
                assertTrue(database.alarmSyncDao().getOutbox("account-1").isEmpty())
                assertFalse(database.alarmSyncDao().getRestoreComplete("account-1"))
            }
        } finally {
            deleteDatabaseFiles(path)
        }
    }

    private suspend fun <T> RingoutDatabase.useDatabase(block: suspend (RingoutDatabase) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    fun `응답이 유실된 upsert는 같은 목적지 ID로 안전하게 재전송한다`() = runBlocking {
        val path = temporaryDatabasePath()
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val session = AuthSession().apply { startNewSession() }
            val accountProvider = DestinationAccountProvider(session) { "account-1" }
            val dao = database.destinationDao()
            val remote = TestDestinationRemoteDataSource()
            val attempts = mutableListOf<RemoteSavedDestination>()
            remote.upsertBlock = { _, destination ->
                attempts += destination
                if (attempts.size == 1) throw IOException("temporary network failure")
                destination
            }
            var now = 1_000L
            val syncer = DestinationOutboxSyncer(
                dao = dao,
                remoteDataSource = remote,
                accountProvider = accountProvider,
                sessionGate = DestinationSyncSessionGate(),
                authSession = session,
                now = { now },
            )
            val saved = RoomDestinationDataSource(dao).save(destination("집"), "account-1")
            val queued = dao.getDueOutbox("account-1", now).single()

            assertEquals(2_000L, syncer.flush())
            assertTrue(dao.getDueOutbox("account-1", now).isEmpty())
            assertEquals(2_000L, syncer.flush())
            assertEquals(1, attempts.size)
            assertEquals(queued.remoteDestinationId, attempts[0].id)
            assertEquals(saved.label, attempts[0].alias)
            now = 2_000L
            assertNull(syncer.flush())
            assertEquals(2, attempts.size)
            assertEquals(attempts[0].id, attempts[1].id)
            assertEquals(attempts[0].version, attempts[1].version)
            assertTrue(dao.observeOutbox().first().isEmpty())
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `조회 중 추가한 로컬 변경은 늦은 서버 스냅샷에 덮이지 않는다`() = runBlocking {
        val path = temporaryDatabasePath()
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val session = AuthSession().apply { startNewSession() }
            val accountProvider = DestinationAccountProvider(session) { "account-1" }
            val gate = DestinationSyncSessionGate()
            val dao = database.destinationDao()
            val dataSource = RoomDestinationDataSource(dao)
            val repository = DefaultDestinationRepository(dataSource, accountProvider, gate)
            val remote = TestDestinationRemoteDataSource()
            val requestStarted = CompletableDeferred<Unit>()
            val returnSnapshot = CompletableDeferred<List<RemoteSavedDestination>>()
            remote.fetchAllBlock = {
                requestStarted.complete(Unit)
                returnSnapshot.await()
            }
            remote.upsertBlock = { _, destination -> destination }
            val syncer = DestinationOutboxSyncer(
                dao = dao,
                remoteDataSource = remote,
                accountProvider = accountProvider,
                sessionGate = gate,
                authSession = session,
            )
            val syncing = async { syncer.flush() }
            requestStarted.await()

            val saved = repository.save(destination("내가 저장한 집"))
            val entity = dao.observeAll("account-1").first().single()
            returnSnapshot.complete(
                listOf(
                    RemoteSavedDestination(
                        id = checkNotNull(entity.remoteDestinationId),
                        alias = "오래된 서버 값",
                        latitude = 0.0,
                        longitude = 0.0,
                        version = 99L,
                    ),
                ),
            )

            assertNull(syncing.await())
            assertEquals(saved.id, dao.observeAll("account-1").first().single().id)
            assertEquals("내가 저장한 집", dataSource.observeAll("account-1").first().single().label)
            val synced = dao.observeAll("account-1").first().single()
            assertEquals(entity.remoteDestinationId, synced.remoteDestinationId)
            assertEquals(1L, synced.destinationVersion)
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `전송 중에도 로컬 저장은 끝나고 로그아웃 뒤 늦은 응답은 Room을 채우지 않는다`() = runBlocking {
        val path = temporaryDatabasePath()
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val session = AuthSession().apply { startNewSession() }
            val identity = checkNotNull(session.identity.value)
            val accountProvider = DestinationAccountProvider(session) { "account-1" }
            val gate = DestinationSyncSessionGate()
            val dao = database.destinationDao()
            val dataSource = RoomDestinationDataSource(dao)
            val repository = DefaultDestinationRepository(dataSource, accountProvider, gate)
            repository.save(destination("전송 중인 목적지"))

            val remote = TestDestinationRemoteDataSource()
            val requestStarted = CompletableDeferred<RemoteSavedDestination>()
            val allowResponse = CompletableDeferred<Unit>()
            remote.upsertBlock = { _, destination ->
                requestStarted.complete(destination)
                allowResponse.await()
                destination
            }
            val syncer = DestinationOutboxSyncer(
                dao = dao,
                remoteDataSource = remote,
                accountProvider = accountProvider,
                sessionGate = gate,
                authSession = session,
            )
            val syncing = async { syncer.flush() }
            requestStarted.await()

            val savedDuringRequest = repository.save(destination("네트워크 응답 전 저장"))
            assertTrue(repository.observeAll().first().any { it.id == savedDuringRequest.id })
            RoomLocalUserDataCleanup(
                dao = database.localUserDataCleanupDao(),
                destinationSessionGate = gate,
                currentSessionIdentity = { identity },
            ).clear()
            allowResponse.complete(Unit)

            syncing.await()
            assertTrue(dao.observeAll("account-1").first().isEmpty())
            assertTrue(dao.observeOutbox().first().isEmpty())
            assertNull(dao.getSyncState("account-1"))
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `과거 Android 기록은 확인된 시각만 한 번 전송 대기로 옮긴다`() = runBlocking {
        val path = temporaryDatabasePath()
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val activity = AlarmActivityEntity.rang(
                alarmId = "alarm-1",
                occurrenceId = "legacy-root",
                timestamp = AlarmActivityTimestamp(1_100, "2026-11-01"),
                scheduleVersion = 3,
            )
            database.alarmActivityDao().record(activity)
            database.missionHistoryDao().insert(MissionHistoryEntity(
                result = "SUCCESS",
                completedAt = "2026-11-01",
                occurrenceId = "legacy-root",
            ))
            val times = AlarmOccurrenceTimesEntity(
                occurrenceId = "legacy-root",
                ringingStartedAtEpochMillis = 1_100,
                ringingStoppedAtEpochMillis = 1_800,
                missionCompletedAtEpochMillis = 3_000,
                ringingScheduledAtEpochMillis = 1_000,
            )
            database.alarmActivityDao().mergeOccurrenceTimes(times)
            val dao = database.alarmOccurrenceSyncDao()

            assertTrue(dao.migrateLegacyAndroidRingingToAccount("account-1", activity, times,
                MissionHistoryEntity(result = "SUCCESS", completedAt = "2026-11-01", occurrenceId = "legacy-root")))
            assertTrue(dao.migrateLegacyAndroidRingingToAccount("account-1", activity, times,
                MissionHistoryEntity(result = "SUCCESS", completedAt = "2026-11-01", occurrenceId = "legacy-root")))

            val events = dao.getEvents("account-1", "legacy-root")
            assertEquals(listOf(AlarmOccurrenceOutboxKind.START, AlarmOccurrenceOutboxKind.ARRIVED), events.map { it.kind })
            assertEquals(1, events.count { it.kind == AlarmOccurrenceOutboxKind.START })
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `예정 시각이 없거나 부모 없는 재울림 ID는 outbox를 만들지 않는다`() = runBlocking {
        val path = temporaryDatabasePath()
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val dao = database.alarmOccurrenceSyncDao()
            val root = AlarmActivityEntity.rang(
                alarmId = "alarm-1",
                occurrenceId = "legacy-local",
                timestamp = AlarmActivityTimestamp(1_100, "2026-11-01"),
                scheduleVersion = 3,
            )
            val incompleteTimes = AlarmOccurrenceTimesEntity(
                occurrenceId = "legacy-local",
                ringingStartedAtEpochMillis = 1_100,
            )
            assertTrue(dao.migrateLegacyAndroidRingingToAccount("account-1", root, incompleteTimes, null))
            assertNotNull(dao.getExecution("account-1", "legacy-local"))
            assertTrue(dao.getEvents("account-1", "legacy-local").isEmpty())

            val retry = root.copy(eventKey = "rang:legacy-local:retry-1")
            assertFalse(dao.migrateLegacyAndroidRingingToAccount("account-1", retry, incompleteTimes, null))
            assertTrue(dao.getEvents("account-1", "legacy-local").isEmpty())
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `첫 로그인 이관은 iOS 로컬 체인의 확인된 root 이벤트를 한 번 복원한다`() = runBlocking {
        val path = temporaryDatabasePath()
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val decision = IosAlarmOccurrenceRecordEntity(
                chainId = "legacy-ios-root",
                rootOccurrenceId = "legacy-ios-root",
                state = IosAlarmOccurrenceRecordState.LOCAL_ONLY,
                ownerAccountId = null,
                alarmId = "alarm-1",
                scheduleVersion = 3,
                scheduledAtEpochMillis = 1_000,
                startedAtEpochMillis = 1_000,
            )
            val activity = database.alarmActivityDao()
            val dao = database.alarmOccurrenceSyncDao()
            dao.keepIosRecordLocalOnly(decision)
            activity.record(AlarmActivityEntity.rang(
                alarmId = "alarm-1",
                occurrenceId = "legacy-ios-root",
                timestamp = AlarmActivityTimestamp(1_100, "2026-11-01"),
                scheduleVersion = 3,
            ))
            database.missionHistoryDao().insert(MissionHistoryEntity(
                result = "SUCCESS",
                completedAt = "2026-11-01",
                occurrenceId = "legacy-ios-root",
            ))
            activity.mergeOccurrenceTimes(AlarmOccurrenceTimesEntity(
                occurrenceId = "legacy-ios-root",
                ringingStartedAtEpochMillis = 1_100,
                ringingStoppedAtEpochMillis = 1_800,
                missionCompletedAtEpochMillis = 3_000,
                ringingScheduledAtEpochMillis = 1_000,
            ))

            assertEquals(IosAlarmOccurrenceRecordState.SERVER,
                dao.migrateLegacyIosDecisionToAccount("legacy-ios-root", "account-1"))
            assertEquals(listOf(
                AlarmOccurrenceOutboxKind.START,
                AlarmOccurrenceOutboxKind.INITIAL_DISMISSED,
            ), dao.getEvents("account-1", "legacy-ios-root").map { it.kind })
            assertTrue(dao.migrateLegacyIosTerminalToAccount("legacy-ios-root", "account-1"))
            val events = dao.getEvents("account-1", "legacy-ios-root")
            assertEquals(listOf(
                AlarmOccurrenceOutboxKind.START,
                AlarmOccurrenceOutboxKind.INITIAL_DISMISSED,
                AlarmOccurrenceOutboxKind.ARRIVED,
            ), events.map { it.kind })
            assertEquals(listOf(1_000L, 1_800L, 3_000L), events.map { it.occurredAtEpochMillis })

            assertNull(dao.migrateLegacyIosDecisionToAccount("legacy-ios-root", "account-1"))
            assertTrue(dao.migrateLegacyIosTerminalToAccount("legacy-ios-root", "account-1"))
            assertEquals(3, dao.getEvents("account-1", "legacy-ios-root").size)
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `필수 시각이 빠진 iOS 레거시 기록은 계정에 보존하되 outbox를 만들지 않는다`() = runBlocking {
        val path = temporaryDatabasePath()
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val dao = database.alarmOccurrenceSyncDao()
            dao.keepIosRecordLocalOnly(IosAlarmOccurrenceRecordEntity(
                chainId = "incomplete-ios-root",
                rootOccurrenceId = "incomplete-ios-root",
                state = IosAlarmOccurrenceRecordState.LOCAL_ONLY,
                ownerAccountId = null,
                alarmId = "alarm-1",
                scheduleVersion = 3,
                scheduledAtEpochMillis = null,
                startedAtEpochMillis = 1_100,
            ))

            assertEquals(IosAlarmOccurrenceRecordState.LOCAL_ONLY,
                dao.migrateLegacyIosDecisionToAccount("incomplete-ios-root", "account-1"))
            assertEquals("account-1", dao.getIosRecordDecision("incomplete-ios-root")?.ownerAccountId)
            assertTrue(dao.getEvents("account-1", "incomplete-ios-root").isEmpty())
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `다른 계정 소유 알람의 미귀속 iOS 기록은 첫 로그인 계정에 넘기지 않는다`() = runBlocking {
        val path = temporaryDatabasePath()
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val otherAccountAlarm = savedAlarm().let { alarm ->
                alarm.copy(request = alarm.request.copy(ownerAccountId = "account-2"))
            }
            RoomAlarmDataSource(database.alarmDao()).replace(otherAccountAlarm)
            val dao = database.alarmOccurrenceSyncDao()
            dao.keepIosRecordLocalOnly(IosAlarmOccurrenceRecordEntity(
                chainId = "conflicting-ios-root",
                rootOccurrenceId = "conflicting-ios-root",
                state = IosAlarmOccurrenceRecordState.LOCAL_ONLY,
                ownerAccountId = null,
                alarmId = "alarm-1",
                scheduleVersion = 3,
                scheduledAtEpochMillis = 1_000,
                startedAtEpochMillis = 1_000,
            ))

            assertNull(dao.migrateLegacyIosDecisionToAccount("conflicting-ios-root", "account-1"))
            assertNull(dao.getIosRecordDecision("conflicting-ios-root")?.ownerAccountId)
            assertNull(dao.getExecution("account-1", "conflicting-ios-root"))
            assertTrue(dao.getEvents("account-1", "conflicting-ios-root").isEmpty())
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `버전 십사와 십오에서 알람과 숲 데이터를 보존하며 소유자 열을 추가한다`() = runBlocking {
        val previousVersions = mapOf(
            14 to "d9d139b7d5784a0097c765a22affc38e",
            15 to "03aae7274de4a40f2e006cdf2afd3de3",
        )
        for ((version, identityHash) in previousVersions) {
            val path = temporaryDatabasePath()
            try {
                val original = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
                try {
                    RoomAlarmDataSource(original.alarmDao()).replace(savedAlarm())
                } finally {
                    original.close()
                }
                createMigrationTestSQLiteDriver().open(path).use { connection ->
                    removeDestinationSyncSchemaForVersion16(connection)
                    connection.execSQL("DROP INDEX IF EXISTS index_alarms_owner_account_id_remote_alarm_id")
                    connection.execSQL("ALTER TABLE alarms DROP COLUMN owner_account_id")
                    if (version == 14) {
                        connection.execSQL("DROP TABLE forest_trees")
                        connection.execSQL("DROP TABLE forest_state")
                    } else {
                        connection.execSQL("UPDATE forest_state SET onboarding_completed_at_epoch_millis = 123 WHERE id = 1")
                        connection.execSQL(
                            "INSERT INTO forest_trees (growth_cycle, kind, slot_index, planted_at_epoch_millis) " +
                                "VALUES (1, 'pine', 0, 456)",
                        )
                    }
                    setDatabaseVersion(connection, version, identityHash)
                }

                val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
                try {
                    val alarm = assertNotNull(RoomAlarmDataSource(database.alarmDao()).getById("alarm-1"))
                    assertEquals(savedAlarm().request.time, alarm.request.time)
                    assertNull(alarm.request.ownerAccountId)
                    assertDatabaseVersion(path, expectedVersion = 19)
                    createMigrationTestSQLiteDriver().open(path).use { connection ->
                        connection.prepare("SELECT onboarding_completed_at_epoch_millis FROM forest_state WHERE id = 1")
                            .use { statement ->
                                assertTrue(statement.step())
                                if (version == 14) assertTrue(statement.isNull(0))
                                else assertEquals(123L, statement.getLong(0))
                            }
                        connection.prepare("SELECT planted_at_epoch_millis FROM forest_trees WHERE growth_cycle = 1")
                            .use { statement ->
                                if (version == 14) assertFalse(statement.step())
                                else {
                                    assertTrue(statement.step())
                                    assertEquals(456L, statement.getLong(0))
                                }
                            }
                    }
                } finally {
                    database.close()
                }
            } finally {
                deleteDatabaseFiles(path)
            }
        }
    }

    fun `버전 십칠의 전송 대기 이벤트를 보존하고 월별 캐시는 원자적으로 저장한다`() = runBlocking {
        val path = temporaryDatabasePath()
        var database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            database.alarmOccurrenceSyncDao().recordStart(
                AlarmOccurrenceSyncEntity("local", "account-1", "alarm", 1, 1_000, 1_100),
            )
            database.close()
            createMigrationTestSQLiteDriver().open(path).use { connection ->
                connection.execSQL("DROP TABLE alarm_occurrence_cache_ringings")
                connection.execSQL("DROP TABLE alarm_occurrence_cache")
                connection.execSQL("DROP TABLE alarm_occurrence_cache_months")
                setDatabaseVersion(connection, 17, "e3680293be6b815b3a9c83012e23b3c2")
            }

            database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
            val cache = database.alarmOccurrenceCacheDao()
            val occurrence = CachedAlarmOccurrenceEntity(
                "account-1", "2026-11", "remote", "alarm", "2026-11-30", "23:59", null, null,
            )
            val ringing = CachedAlarmRingingEntity("account-1", "remote", "INITIAL", null, 1_200, null)
            assertFails {
                cache.saveMonth("account-1", "2026-11", listOf(occurrence), listOf(ringing, ringing))
            }
            assertFalse(cache.isComplete("account-1", "2026-11"))
            assertTrue(cache.getOccurrences("account-1").isEmpty())

            cache.saveMonth("account-1", "2026-11", listOf(occurrence), listOf(ringing))
            cache.saveMonth("account-2", "2026-11", emptyList(), emptyList())
            assertEquals(1, database.alarmOccurrenceSyncDao().getUnsentEvents("account-1").size)
            assertEquals(0, database.arrivalResultDao().getSuccessfulDayCount())
            assertDatabaseVersion(path, expectedVersion = 19)
            database.close()

            database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
            val reopenedCache = database.alarmOccurrenceCacheDao()
            assertTrue(reopenedCache.isComplete("account-1", "2026-11"))
            assertTrue(reopenedCache.isComplete("account-2", "2026-11"))
            assertTrue(reopenedCache.getOccurrences("account-2").isEmpty())
            val row = reopenedCache.getOccurrences("account-1").single()
            assertEquals(occurrence, row.occurrence)
            assertEquals(listOf(ringing), row.ringings)
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `버전 십육의 저장 목적지를 보존하고 목적지 동기화 테이블을 추가한다`() = runBlocking {
        val path = temporaryDatabasePath()
        val original = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        val saved = try {
            destinationRepository(original).save(destination("기존 목적지"))
        } finally {
            original.close()
        }

        try {
            createMigrationTestSQLiteDriver().open(path).use { connection ->
                connection.execSQL(
                    "DROP INDEX index_saved_destinations_owner_account_id_remote_destination_id",
                )
                connection.execSQL("DROP TABLE destination_outbox")
                connection.execSQL("DROP TABLE destination_sync_state")
                connection.execSQL("ALTER TABLE saved_destinations DROP COLUMN owner_account_id")
                connection.execSQL("ALTER TABLE saved_destinations DROP COLUMN remote_destination_id")
                connection.execSQL("ALTER TABLE saved_destinations DROP COLUMN destination_version")
                setDatabaseVersion(connection, version = 16, identityHash = "4c3fbe33696f952425fc0510f29f39c3")
            }

            val migrated = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
            try {
                assertEquals(listOf(saved), destinationRepository(migrated).observeAll().first())
                assertTrue(migrated.destinationDao().getDueOutbox("account-1", 0L).isEmpty())
                assertDatabaseVersion(path, expectedVersion = 19)
                createMigrationTestSQLiteDriver().open(path).use { connection ->
                    connection.prepare(
                        "SELECT owner_account_id, remote_destination_id, destination_version " +
                            "FROM saved_destinations WHERE id = ${saved.id}",
                    ).use { statement ->
                        assertTrue(statement.step())
                        assertTrue(statement.isNull(0))
                        assertTrue(statement.isNull(1))
                        assertEquals(1L, statement.getLong(2))
                    }
                }
            } finally {
                migrated.close()
            }
        } finally {
            deleteDatabaseFiles(path)
        }
    }

    fun `저장과 삭제는 outbox와 원자화하고 전송 시도한 upsert 뒤에 삭제를 보낸다`() = runBlocking {
        val path = temporaryDatabasePath()
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val dao = database.destinationDao()
            val dataSource = RoomDestinationDataSource(dao)

            val neverSent = dataSource.save(destination("미전송"), ownerAccountId = "account-1")
            val cancelledUpsert = dao.getDueOutbox("account-1", 0L).single()
            assertEquals(DestinationOutboxOperation.UPSERT, cancelledUpsert.operation)
            assertEquals(neverSent.id, cancelledUpsert.localDestinationId)
            assertEquals(neverSent.label, cancelledUpsert.payloadAlias)
            assertEquals(1L, cancelledUpsert.destinationVersion)
            assertTrue(dataSource.delete(neverSent.id, ownerAccountId = "account-1"))
            assertTrue(dao.getDueOutbox("account-1", 0L).isEmpty())

            val attempted = dataSource.save(destination("전송 시도"), ownerAccountId = "account-1")
            val upsert = dao.getDueOutbox("account-1", 0L).single()
            dao.recordOutboxAttempt("account-1", upsert.id)
            assertTrue(dataSource.delete(attempted.id, ownerAccountId = "account-1"))
            assertEquals(
                listOf(DestinationOutboxOperation.UPSERT, DestinationOutboxOperation.DELETE),
                dao.observeOutbox().first().map { it.operation },
            )

            dao.acknowledgeUpsertOutbox(
                ownerAccountId = "account-1",
                outboxId = upsert.id,
                remoteDestinationId = upsert.remoteDestinationId,
                serverDestination = RemoteSavedDestination(
                    id = upsert.remoteDestinationId,
                    alias = checkNotNull(upsert.payloadAlias),
                    latitude = checkNotNull(upsert.payloadLatitude),
                    longitude = checkNotNull(upsert.payloadLongitude),
                    version = checkNotNull(upsert.destinationVersion),
                ),
            )
            assertEquals(
                DestinationOutboxOperation.DELETE,
                dao.getDueOutbox("account-1", 0L).single().operation,
            )
            assertEquals(upsert.remoteDestinationId, dao.getDueOutbox("account-1", 0L).single().remoteDestinationId)
            assertTrue(dataSource.observeAll("account-1").first().isEmpty())
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `서버 목적지 복원을 반복해도 로컬 ID를 유지하고 행을 중복 생성하지 않는다`() = runBlocking {
        val path = temporaryDatabasePath()
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val dao = database.destinationDao()
            val first = com.joon.ringout.data.destination.RemoteSavedDestination(
                id = "server-id",
                alias = "집",
                latitude = 37.5,
                longitude = 127.0,
                version = 2,
            )
            dao.applyRemoteSnapshot("account-1", listOf(first))
            val original = dao.observeAll("account-1").first().single()

            dao.applyRemoteSnapshot("account-1", listOf(first.copy(alias = "새 집", version = 3)))
            val restored = dao.observeAll("account-1").first().single()

            assertEquals(original.id, restored.id)
            assertEquals("새 집", restored.label)
            assertEquals(3L, restored.destinationVersion)
            assertTrue(dao.getDueOutbox("account-1", 0L).isEmpty())
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `버전 십삼의 성공과 실패 기록을 보존하고 도착 성장은 빈 상태로 시작한다`() = runBlocking {
        val path = temporaryDatabasePath()
        createVersionThirteenDatabase(path)
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val arrivals = RoomArrivalResultRepository(database.arrivalResultDao())
            val history = database.missionHistoryDao().getHistoryWithTimes("2026-11-01", "2026-11-30")

            assertEquals(listOf("SUCCESS", "FAILURE"), history.map { it.history.result })
            assertEquals(listOf("legacy-success", "legacy-failure"), history.map { it.history.occurrenceId })
            assertEquals(listOf(1_000L, 2_000L), history.map { it.missionCompletedAtEpochMillis })
            assertEquals(0, arrivals.getGrowth().successfulDayCount)
            assertNull(arrivals.getResult("legacy-success"))
            assertNull(arrivals.observeLatestUnacknowledgedResult().first())
            assertNull(RoomAlarmDataSource(database.alarmDao()).getById("alarm-1")?.request?.ownerAccountId)
            assertTrue(growthActivationAt(path) > 0)
            assertDatabaseVersion(path, expectedVersion = 19)
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `마이그레이션으로 저장한 성장 활성화 기준은 재실행해도 바뀌지 않는다`() = runBlocking {
        val path = temporaryDatabasePath()
        createVersionThirteenDatabase(path)
        var database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            RoomArrivalResultRepository(database.arrivalResultDao()).getGrowth()
            assertTrue(growthActivationAt(path) > 0)
            database.close()
            // A fixed past marker makes this check independent of clock resolution or execution speed.
            createMigrationTestSQLiteDriver().open(path).use { connection ->
                connection.execSQL("UPDATE arrival_growth_activation SET activated_at_epoch_millis = 123456")
            }

            database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
            assertEquals(0, RoomArrivalResultRepository(database.arrivalResultDao()).getGrowth().successfulDayCount)
            assertEquals(123_456L, growthActivationAt(path))
            assertDatabaseVersion(path, expectedVersion = 19)
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `마이그레이션 이전 도착을 복구해도 성장하지 않고 활성화 시각부터 도착을 집계한다`() = runBlocking {
        val path = temporaryDatabasePath()
        createVersionThirteenDatabase(path)
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val arrivals = RoomArrivalResultRepository(database.arrivalResultDao())
            val history = RoomMissionHistoryDataSource(database.missionHistoryDao())
            assertEquals(0, arrivals.getGrowth().successfulDayCount)
            val activatedAt = growthActivationAt(path)

            assertTrue(history.record(MissionHistoryDto(
                result = "SUCCESS",
                completedAt = "2026-11-09",
                occurrenceId = "recovered-before-activation",
                missionCompletedAtEpochMillis = activatedAt - 1,
            )))
            assertNull(arrivals.getResult("recovered-before-activation"))
            assertEquals(0, arrivals.getGrowth().successfulDayCount)

            assertTrue(history.record(MissionHistoryDto(
                result = "SUCCESS",
                completedAt = "2026-11-09",
                occurrenceId = "arrival-at-activation",
                missionCompletedAtEpochMillis = activatedAt,
            )))
            val result = assertNotNull(arrivals.getResult("arrival-at-activation"))
            assertEquals(1, result.successfulDayCountAtArrival)
            assertTrue(result.grewOnThisArrival)
            assertEquals(1, arrivals.getGrowth().successfulDayCount)
            assertEquals(4, database.missionHistoryDao().getHistory("2026-11-01", "2026-11-30").size)
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `버전 십이에서 label을 보정하고 알람과 연관 저장 데이터를 보존한다`() = runBlocking {
        val path = temporaryDatabasePath()
        var database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
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

            createMigrationTestSQLiteDriver().open(path).use { connection ->
                downgradeCurrentSchemaColumnsToVersion12(connection)
                connection.execSQL("UPDATE saved_destinations SET name = char(9, 32), address = ' 서울 주소 ' WHERE id = 2")
                connection.execSQL("UPDATE saved_destinations SET name = char(9, 32), address = char(9, 32) WHERE id = 3")
                connection.execSQL("UPDATE alarms SET destination_address = '서울특별시 중구 세종대로 110' WHERE id = 'alarm-1'")
                connection.execSQL("UPDATE alarms SET destination_name = char(9, 32), destination_address = '부산 주소' WHERE id = 'alarm-address-fallback'")
                connection.execSQL("UPDATE alarms SET destination_name = char(9, 32), destination_address = char(9, 32) WHERE id = 'alarm-empty-fallback'")
                setDatabaseVersion(connection, 12, "7f803ac5d546788e54963c61d86ea315")
            }

            database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
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
            createMigrationTestSQLiteDriver().open(path).use { connection ->
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

    fun `버전 십일에서 iOS 기록 판단 테이블을 추가하고 로컬 전용 결정을 보존한다`() = runBlocking {
        val path = temporaryDatabasePath()
        var database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            database.alarmOccurrenceSyncDao().getPendingIosRecordDecisions()
            database.close()
            createMigrationTestSQLiteDriver().open(path).use { connection ->
                downgradeCurrentSchemaColumnsToVersion12(connection)
                connection.execSQL("DROP TABLE ios_alarm_occurrence_recording")
                setDatabaseVersion(connection, 11, "48a8b44654750f7b165ea3a10ff4ffb9")
            }
            database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
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
            assertDatabaseVersion(path, expectedVersion = 19)
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `버전 십의 미전송 알람을 보존하고 과거 이동 이벤트를 임의 생성하지 않는다`() = runBlocking {
        val path = temporaryDatabasePath()
        var database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val sync = database.alarmOccurrenceSyncDao()
            sync.recordStart(com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncEntity(
                "existing", "1", "alarm", 1, 1_000, 1_001,
            ))
            sync.recordDismissal("1", "existing", 2_000)
            database.close()
            // 현재 버전에서 v12의 열 구조와 v10에 없던 테이블을 되돌려 실제 v10 저장 파일을 구성한다.
            createMigrationTestSQLiteDriver().open(path).use { connection ->
                downgradeCurrentSchemaColumnsToVersion12(connection)
                connection.execSQL("DROP TABLE ios_alarm_occurrence_recording")
                connection.execSQL("DROP TABLE alarm_movement_outbox")
                setDatabaseVersion(connection, 10, "91e792c227f600707f21ca662a258bb9")
            }
            database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
            val migrated = database.alarmOccurrenceSyncDao()
            assertEquals(2, migrated.getUnsentEvents("1").size)
            assertEquals(2_000L, migrated.getEvents("1", "existing").last().occurredAtEpochMillis)
            assertTrue(migrated.getUnsentMovements("1").isEmpty())
            migrated.recordTerminal("1", "existing", com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxKind.ARRIVED, 3_000)
            assertEquals(com.joon.ringout.domain.alarmmovement.AlarmMovementAction.ARRIVE,
                migrated.getUnsentMovements("1").single().action)
            assertDatabaseVersion(path, expectedVersion = 19)
        } finally {
            database.close()
            deleteDatabaseFiles(path)
        }
    }

    fun `버전 구의 기존 데이터를 보존하고 빈 알람 전송 저장소를 추가한다`() = runBlocking<Unit> {
        val path = temporaryDatabasePath()
        createVersionFiveDatabase(path)
        createMigrationTestSQLiteDriver().open(path).use { connection ->
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
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
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

    fun `버전 칠의 실행 시각을 보존하고 예정 시각은 추정하지 않은 채 새 열을 추가한다`() = runBlocking {
        val path = temporaryDatabasePath()
        createVersionFiveDatabase(path)
        createMigrationTestSQLiteDriver().open(path).use { connection ->
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
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
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

    fun `버전 육의 울림 이벤트에서 시작 시각만 복원하고 없는 종료와 완료 시각은 비워 둔다`() = runBlocking<Unit> {
        val path = temporaryDatabasePath()
        createVersionFiveDatabase(path)
        createMigrationTestSQLiteDriver().open(path).use { connection ->
            connection.execSQL("DELETE FROM alarm_activity_events WHERE type = 'CREATED'")
            connection.execSQL("UPDATE mission_history SET occurrence_id = 'one', completed_at = '2026-09-28'")
            connection.execSQL("PRAGMA user_version = 6")
        }
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
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

    fun `버전 오에서 생성 집계 이벤트만 제거하고 울림과 기존 알람을 보존한다`() = runBlocking {
        val path = temporaryDatabasePath()
        createVersionFiveDatabase(path)
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
        try {
            val counts = database.alarmActivityDao().observeCounts("2026-09-28").first()
            assertEquals(1, counts.ringingCount)
            assertEquals("2026-09-28", counts.trackingStartDate)
            assertNotNull(database.alarmDao().getById("alarm-v2"))
            assertEquals(1, database.missionHistoryDao().getHistory("2026-08-01", "2026-08-31").size)
            assertEquals("rang:one", database.alarmActivityDao().getObservations().single().eventKey)
            createMigrationTestSQLiteDriver().open(path).use { connection ->
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

    fun `버전 사에서 기존 알람과 미션을 보존하고 활동 집계 테이블을 추가한다`() = runBlocking {
        val path = temporaryDatabasePath()
        createVersionThreeDatabase(path)
        createMigrationTestSQLiteDriver().open(path).use { connection ->
            connection.execSQL("ALTER TABLE mission_history ADD COLUMN occurrence_id TEXT")
            connection.execSQL("CREATE UNIQUE INDEX index_mission_history_occurrence_id ON mission_history (occurrence_id)")
            connection.execSQL("PRAGMA user_version = 4")
        }
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
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

    fun `버전 일에서 미션 기록을 보존하며 현재 버전으로 마이그레이션한다`() = runBlocking {
        val databasePath = temporaryDatabasePath()
        createVersionOneDatabase(databasePath)

        val database = buildMigrationTestDatabase(
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

    fun `버전 이에서 기존 테이블을 보존하고 저장 목적지를 추가한다`() = runBlocking {
        val databasePath = temporaryDatabasePath()
        createVersionTwoDatabase(databasePath)

        val database = buildMigrationTestDatabase(
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

    fun `버전 삼에서 기존 행을 보존하고 발생 ID 중복을 막는다`() = runBlocking {
        val databasePath = temporaryDatabasePath()
        createVersionThreeDatabase(databasePath)

        val database = buildMigrationTestDatabase(
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

    private suspend fun createVersionThirteenDatabase(databasePath: String) {
        val database = buildMigrationTestDatabase(Room.databaseBuilder<RingoutDatabase>(name = databasePath))
        try {
            RoomAlarmDataSource(database.alarmDao()).replace(savedAlarm())
            val history = RoomMissionHistoryDataSource(database.missionHistoryDao())
            history.record(MissionHistoryDto(
                result = "SUCCESS",
                completedAt = "2026-11-01",
                occurrenceId = "legacy-success",
                missionCompletedAtEpochMillis = 1_000,
            ))
            history.record(MissionHistoryDto(
                result = "FAILURE",
                completedAt = "2026-11-02",
                occurrenceId = "legacy-failure",
                missionCompletedAtEpochMillis = 2_000,
            ))
        } finally {
            database.close()
        }
        createMigrationTestSQLiteDriver().open(databasePath).use { connection ->
            removeArrivalTables(connection)
            removeDestinationSyncSchemaForVersion16(connection)
            connection.execSQL("DROP INDEX IF EXISTS index_alarms_owner_account_id_remote_alarm_id")
            connection.execSQL("ALTER TABLE alarms DROP COLUMN owner_account_id")
            // Identity hash from the committed Room schema 13.json.
            setDatabaseVersion(connection, 13, "311bb99f2c095756c5ded6fc1066d941")
        }
    }

    private fun growthActivationAt(databasePath: String): Long =
        createMigrationTestSQLiteDriver().open(databasePath).use { connection ->
            connection.prepare("SELECT activated_at_epoch_millis FROM arrival_growth_activation").use { statement ->
                assertTrue(statement.step())
                val activatedAt = statement.getLong(0)
                assertFalse(statement.step())
                activatedAt
            }
        }

    private fun createVersionFiveDatabase(databasePath: String) {
        createVersionThreeDatabase(databasePath)
        createMigrationTestSQLiteDriver().open(databasePath).use { connection ->
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
        createMigrationTestSQLiteDriver().open(databasePath).use { connection ->
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
        createMigrationTestSQLiteDriver().open(databasePath).use { connection ->
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
        createMigrationTestSQLiteDriver().open(databasePath).use { connection ->
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

    private fun downgradeCurrentSchemaColumnsToVersion12(
        connection: androidx.sqlite.SQLiteConnection,
    ) {
        removeArrivalTables(connection)
        removeDestinationSyncSchemaForVersion16(connection)
        connection.execSQL("DROP INDEX IF EXISTS index_alarms_owner_account_id_remote_alarm_id")
        connection.execSQL("ALTER TABLE alarms DROP COLUMN owner_account_id")
        connection.execSQL("ALTER TABLE saved_destinations ADD COLUMN address TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE saved_destinations RENAME COLUMN label TO name")
        connection.execSQL("ALTER TABLE alarms ADD COLUMN destination_address TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE alarms RENAME COLUMN destination_label TO destination_name")
    }

    private fun removeArrivalTables(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL("DROP TABLE forest_trees")
        connection.execSQL("DROP TABLE forest_state")
        connection.execSQL("DROP TABLE arrival_results")
        connection.execSQL("DROP TABLE arrival_growth_activation")
    }

    private fun removeDestinationSyncSchemaForVersion16(
        connection: androidx.sqlite.SQLiteConnection,
    ) {
        connection.execSQL("DROP INDEX index_saved_destinations_owner_account_id_remote_destination_id")
        connection.execSQL("DROP TABLE destination_outbox")
        connection.execSQL("DROP TABLE destination_sync_state")
        connection.execSQL("ALTER TABLE saved_destinations DROP COLUMN owner_account_id")
        connection.execSQL("ALTER TABLE saved_destinations DROP COLUMN remote_destination_id")
        connection.execSQL("ALTER TABLE saved_destinations DROP COLUMN destination_version")
    }

    private fun setDatabaseVersion(
        connection: androidx.sqlite.SQLiteConnection,
        version: Int,
        identityHash: String,
    ) {
        removeAlarmSyncSchemaForVersion18(hasIosRecordTable = version >= 12).forEach(connection::execSQL)
        connection.execSQL("UPDATE room_master_table SET identity_hash = '$identityHash' WHERE id = 42")
        connection.execSQL("PRAGMA user_version = $version")
    }

    private fun assertDatabaseVersion(path: String, expectedVersion: Int) {
        createMigrationTestSQLiteDriver().open(path).use { connection ->
            connection.prepare("PRAGMA user_version").use { statement ->
                assertTrue(statement.step())
                assertEquals(expectedVersion, statement.getLong(0).toInt())
            }
        }
    }

    private fun temporaryDatabasePath(): String =
        createMigrationTestDatabasePath()

    private fun deleteDatabaseFiles(databasePath: String) =
        deleteMigrationTestDatabaseFiles(databasePath)

    private fun buildMigrationTestDatabase(
        builder: androidx.room3.RoomDatabase.Builder<RingoutDatabase>,
    ): RingoutDatabase =
        com.joon.ringout.data.database.buildRingoutDatabase(
            builder = builder,
            driver = createMigrationTestSQLiteDriver(),
        )
}

private class TestDestinationRemoteDataSource : DestinationRemoteDataSource {
    var fetchAllBlock: suspend (String) -> List<RemoteSavedDestination> = { emptyList() }
    var upsertBlock: suspend (String, RemoteSavedDestination) -> RemoteSavedDestination = { _, destination ->
        destination
    }
    var deleteBlock: suspend (String, String) -> Unit = { _, _ -> }

    override suspend fun fetchAll(ownerAccountId: String): List<RemoteSavedDestination> =
        fetchAllBlock(ownerAccountId)

    override suspend fun upsert(
        ownerAccountId: String,
        destination: RemoteSavedDestination,
    ): RemoteSavedDestination = upsertBlock(ownerAccountId, destination)

    override suspend fun delete(ownerAccountId: String, destinationId: String) =
        deleteBlock(ownerAccountId, destinationId)
}
