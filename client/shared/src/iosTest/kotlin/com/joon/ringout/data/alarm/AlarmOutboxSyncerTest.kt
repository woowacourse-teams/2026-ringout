package com.joon.ringout.data.alarm

import androidx.room3.Room
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import com.joon.ringout.data.destination.DestinationAccountProvider
import com.joon.ringout.data.destination.DestinationSyncSessionGate
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.domain.auth.AuthSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmOutboxSyncerTest {
    @Test
    fun `응답을 잃은 PUT은 전송기를 다시 만들어도 동일한 요청으로 DELETE보다 먼저 재시도한다`() = runBlocking {
        withDatabase { database ->
            val dao = database.alarmSyncDao()
            val firstRequest = remoteAlarm()
            val operations = mutableListOf<String>()
            var failFirstPut = true
            var now = 0L
            val remote = FakeRemote(
                put = { request ->
                    assertEquals(firstRequest, request)
                    operations += "PUT"
                    if (failFirstPut) {
                        failFirstPut = false
                        error("서버 반영 뒤 응답을 읽지 못했습니다.")
                    }
                    request
                },
                remove = { operations += "DELETE" },
            )

            dao.insertOutbox(put(firstRequest))
            dao.insertOutbox(delete(firstRequest.alarmId))
            val firstSyncer = createSyncer(database, remote, now = { now })
            firstSyncer.prepareCurrentSession(forceRestore = false)
            val retryAt = assertNotNull(firstSyncer.flush())

            assertEquals(listOf("PUT"), operations)
            assertEquals(2, dao.getOutbox(Owner).size)

            now = retryAt
            val restartedSyncer = createSyncer(database, remote, now = { now })
            restartedSyncer.prepareCurrentSession(forceRestore = false)
            assertNull(restartedSyncer.flush())

            assertEquals(listOf("PUT", "PUT", "DELETE"), operations)
            assertTrue(dao.getOutbox(Owner).isEmpty())
        }
    }

    @Test
    fun `GET 진행 중 삭제한 알람은 오래된 응답으로 복원되지 않는다`() = runBlocking {
        withDatabase { database ->
            val server = remoteAlarm()
            val local = server.toAlarmWithRepeatDays(DeviceId, Owner)
            database.alarmDao().replace(local.copy(alarm = local.alarm.copy(remoteAlarmId = server.alarmId)))
            var deletedId: String? = null
            val remote = FakeRemote(
                fetch = {
                    database.alarmDao().delete(DeviceId)
                    database.alarmSyncDao().insertOutbox(delete(server.alarmId))
                    listOf(server)
                },
                remove = { deletedId = it },
            )

            val syncer = createSyncer(database, remote)
            syncer.prepareCurrentSession(forceRestore = true)
            assertNull(syncer.flush())

            assertEquals(server.alarmId, deletedId)
            assertTrue(database.alarmDao().getOwnedWithRepeatDays(Owner).isEmpty())
            assertTrue(database.alarmSyncDao().getRestoreComplete(Owner))
        }
    }

    @Test
    fun `서버 ID와 반복 간격 0을 보존하며 같은 로그인에서 재시작하면 기기 설정을 유지한다`() = runBlocking {
        withDatabase { database ->
            val server = remoteAlarm().copy(alarmVersion = 0, repeatTime = 0)
            var fetches = 0
            val remote = FakeRemote(fetch = { fetches++; listOf(server) })

            val syncer = createSyncer(database, remote)
            syncer.prepareCurrentSession(forceRestore = true)
            assertNull(syncer.flush())

            val restored = assertNotNull(database.alarmDao().getById(DeviceId)).alarm
            assertEquals(server.alarmId, restored.remoteAlarmId)
            assertEquals(0L, restored.confirmedAlarmVersion)
            assertEquals(0, restored.remoteRepeatTime)
            assertEquals(1, restored.limitMinutes)
            assertFalse(restored.enabled)
            assertNull(restored.alarmSoundUri)

            database.alarmDao().upsertAlarm(restored.copy(enabled = true, alarmSoundUri = "custom"))
            val restartedSyncer = createSyncer(database, remote)
            restartedSyncer.prepareCurrentSession(forceRestore = false)
            assertNull(restartedSyncer.flush())

            assertEquals(1, fetches)
            val local = assertNotNull(database.alarmDao().getById(DeviceId)).alarm
            assertTrue(local.enabled)
            assertEquals("custom", local.alarmSoundUri)
        }
    }

    @Test
    fun `이전 PUT이 더 높은 서버 버전을 반환해도 최신 로컬 수정은 새 버전으로 전송한다`() = runBlocking {
        withDatabase { database ->
            val initial = remoteAlarm()
            val latest = initial.copy(time = "08:15", alarmVersion = 2)
            val local = latest.toAlarmWithRepeatDays(DeviceId, Owner)
            database.alarmDao().replace(
                local.copy(
                    alarm = local.alarm.copy(enabled = true, remoteAlarmId = latest.alarmId, alarmVersion = 2),
                ),
            )
            database.alarmSyncDao().setRestoreComplete(Owner)
            database.alarmSyncDao().insertOutbox(put(initial))
            database.alarmSyncDao().insertOutbox(put(latest))
            val requests = mutableListOf<RemoteAlarm>()
            val remote = FakeRemote(
                put = { request ->
                    requests += request
                    if (request == initial) initial.copy(time = "09:15", alarmVersion = 5) else request
                },
            )

            val syncer = createSyncer(database, remote) { _, _ ->
                error("최신 로컬 변경을 보내는 동안 예약은 유지해야 합니다.")
            }
            syncer.prepareCurrentSession(forceRestore = false)
            assertNull(syncer.flush())

            assertEquals(listOf(initial, latest.copy(alarmVersion = 6)), requests)
            val stored = assertNotNull(database.alarmDao().getById(DeviceId)).alarm
            assertTrue(stored.enabled)
            assertEquals(latest.time, stored.time)
            assertEquals(6L, stored.confirmedAlarmVersion)
        }
    }

    @Test
    fun `로그아웃 로컬 정리 뒤 도착한 GET 응답은 알람을 다시 만들지 않는다`() = runBlocking {
        withDatabase { database ->
            val authSession = AuthSession().also { it.startNewSession() }
            val gate = DestinationSyncSessionGate()
            val remote = FakeRemote(
                fetch = {
                    gate.clearForLogout(authSession.identity.value) {
                        database.localUserDataCleanupDao().clear()
                    }
                    // The auth repository has not cleared its identity yet.
                    listOf(remoteAlarm())
                },
            )
            val syncer = createSyncer(database, remote, authSession = authSession, gate = gate)
            syncer.prepareCurrentSession(forceRestore = true)
            assertNull(syncer.flush())

            assertTrue(database.alarmDao().getOwnedWithRepeatDays(Owner).isEmpty())
            assertFalse(database.alarmSyncDao().getRestoreComplete(Owner))
        }
    }

    @Test
    fun `400으로 거절된 요청은 멈추고 다른 알람은 전송한다`() = runBlocking {
        withDatabase { database ->
            val rejected = remoteAlarm()
            val accepted = rejected.copy(alarmId = "another")
            val calls = mutableListOf<String>()
            val remote = FakeRemote(
                put = { request ->
                    calls += request.alarmId
                    if (request.alarmId == rejected.alarmId) {
                        throw ApiException(400, "INVALID", "요청 오류")
                    }
                    request
                },
            )
            val dao = database.alarmSyncDao()
            dao.insertOutbox(put(rejected))
            dao.insertOutbox(put(accepted))

            val syncer = createSyncer(database, remote)
            syncer.prepareCurrentSession(forceRestore = false)
            assertNull(syncer.flush())
            assertNull(syncer.flush())

            assertEquals(listOf(rejected.alarmId, accepted.alarmId), calls)
            assertEquals("REJECTED", dao.getOutbox(Owner).single().state)
        }
    }

    @Test
    fun `401로 멈춘 요청은 같은 계정의 새 세션에서 다시 전송한다`() = runBlocking {
        withDatabase { database ->
            val request = remoteAlarm()
            val authSession = AuthSession().also { it.startNewSession() }
            var calls = 0
            val remote = FakeRemote(
                put = {
                    assertEquals(request, it)
                    if (++calls == 1) throw ApiException(401, "UNAUTHORIZED", "로그인 필요")
                    it
                },
            )
            val dao = database.alarmSyncDao()
            dao.insertOutbox(put(request))

            val syncer = createSyncer(database, remote, authSession = authSession)
            syncer.prepareCurrentSession(forceRestore = false)
            assertNull(syncer.flush())
            assertNull(syncer.flush())
            assertEquals(1, calls)
            assertEquals("PENDING", dao.getOutbox(Owner).single().state)

            authSession.startNewSession()
            syncer.prepareCurrentSession(forceRestore = true)
            assertNull(syncer.flush())
            assertEquals(2, calls)
            assertTrue(dao.getOutbox(Owner).isEmpty())
        }
    }

    private fun createSyncer(
        database: RingoutDatabase,
        remote: AlarmRemoteDataSource,
        now: () -> Long = { 0L },
        authSession: AuthSession = AuthSession().also { it.startNewSession() },
        gate: DestinationSyncSessionGate = DestinationSyncSessionGate(),
        applyServerChange: suspend (String, suspend () -> Boolean) -> Boolean = { _, apply -> apply() },
    ): AlarmOutboxSyncer {
        return AlarmOutboxSyncer(
            store = AlarmSyncStore(database) { DeviceId },
            remoteDataSource = remote,
            accountProvider = DestinationAccountProvider(authSession) { Owner },
            sessionGate = gate,
            applyServerChange = applyServerChange,
            now = now,
        )
    }

    private suspend fun withDatabase(block: suspend (RingoutDatabase) -> Unit) {
        val database = buildRingoutDatabase(Room.inMemoryDatabaseBuilder<RingoutDatabase>())
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun put(alarm: RemoteAlarm) =
        AlarmOutboxEntity(ownerAccountId = Owner, remoteAlarmId = alarm.alarmId, payload = Json.encodeToString(alarm))

    private fun delete(alarmId: String) =
        AlarmOutboxEntity(ownerAccountId = Owner, remoteAlarmId = alarmId, payload = null)

    private fun remoteAlarm() = RemoteAlarm(
        alarmId = "Server-id",
        time = "07:15",
        dayOfWeek = emptyList(),
        repeatTime = 5,
        destinationName = "회사",
        destinationLatitude = 37.5,
        destinationLongitude = 127.0,
        alarmVersion = 1,
    )

    private class FakeRemote(
        private val fetch: suspend () -> List<RemoteAlarm> = { emptyList() },
        private val put: suspend (RemoteAlarm) -> RemoteAlarm = { it },
        private val remove: suspend (String) -> Unit = {},
    ) : AlarmRemoteDataSource {
        override suspend fun fetchAll(ownerAccountId: String): List<RemoteAlarm> = fetch()
        override suspend fun upsert(ownerAccountId: String, alarm: RemoteAlarm): RemoteAlarm = put(alarm)
        override suspend fun delete(ownerAccountId: String, alarmId: String) = remove(alarmId)
    }

    private companion object {
        const val Owner = "account"
        const val DeviceId = "D08814F5-0B28-4454-91F0-F6931224EBD6"
    }
}
