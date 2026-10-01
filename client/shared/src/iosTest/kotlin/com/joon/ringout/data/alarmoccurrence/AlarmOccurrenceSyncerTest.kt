package com.joon.ringout.data.alarmoccurrence

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.domain.alarmoccurrence.*
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.io.encoding.Base64
import kotlin.test.*
import kotlin.time.Instant
import kotlin.time.Clock

class AlarmOccurrenceSyncerTest {
    @Test
    fun `실행 루프가 저장된 재시도 시각에 별도 이벤트 없이 다시 전송한다`() = withFixture { f ->
        f.readTime = { Clock.System.now().toEpochMilliseconds() }
        f.start()
        f.remote.beforeStart = { if (f.remote.starts.size == 1) throw IOException("일시 오류") }
        val monitor = object : NetworkMonitor { override val status = MutableStateFlow(NetworkStatus.Online) }
        coroutineScope {
            val job = AlarmOccurrenceSyncCoordinator(f.syncer, f.dao, f.session, monitor, { f.readTime() }).start(this)
            try {
                withTimeout(12_000) { f.dao.observeUnsentEvents().first { it.isEmpty() } }
                assertEquals(2, f.remote.starts.size)
                assertEquals(f.remote.starts.first(), f.remote.starts.last())
            } finally { job.cancelAndJoin() }
        }
    }

    @Test
    fun `POST와 해제 재울림 도착을 순서대로 보내고 완료 이벤트를 다시 보내지 않는다`() = withFixture { f ->
        f.start()
        f.dao.recordDismissal("1", "root", 2_000)
        f.dao.recordRepeat("1", "root", "retry", "repeat-id", 3_000)
        f.dao.recordDismissal("1", "retry", 4_000)
        f.dao.recordTerminal("1", "retry", AlarmOccurrenceOutboxKind.ARRIVED, 5_000)
        assertNull(f.syncer.flush())
        assertEquals(listOf("POST", "InitialDismissed", "RepeatRang", "RepeatDismissed", "Arrived"), f.remote.calls)
        assertEquals(ServerId, f.dao.getExecution("1", "root")?.serverOccurrenceId)
        assertTrue(f.dao.getUnsentEvents("1").isEmpty())
        val repeated = f.remote.events[2] as AlarmOccurrenceEvent.RepeatDismissed
        assertEquals("repeat-id", repeated.ringing.eventId)
        assertEquals(Instant.fromEpochMilliseconds(3_000), repeated.ringing.ringingAt)
        f.syncer.flush()
        assertEquals(5, f.remote.calls.size)
    }

    @Test
    fun `POST 응답을 잃으면 같은 본문으로 재전송한 뒤에만 PATCH를 보낸다`() = withFixture { f ->
        f.start()
        f.dao.recordDismissal("1", "root", 2_000)
        f.remote.beforeStart = { if (f.remote.starts.size == 1) throw IOException("응답 유실") }
        assertEquals(15_000L, f.syncer.flush())
        assertEquals(listOf("POST"), f.remote.calls)
        assertNull(f.dao.getExecution("1", "root")?.serverOccurrenceId)
        assertEquals(1, f.dao.getEvents("1", "root").first().attemptCount)
        f.syncer.flush()
        assertEquals(1, f.remote.starts.size)
        f.time = 15_000
        f.newSyncer().flush()
        assertEquals(listOf("POST", "POST", "InitialDismissed"), f.remote.calls)
        assertEquals(f.remote.starts.first(), f.remote.starts.last())
        assertTrue(f.dao.getUnsentEvents("1").isEmpty())
    }

    @Test
    fun `PATCH 실패 시 후속 이벤트를 보류하고 같은 시각으로 재시도한다`() = withFixture { f ->
        f.start()
        f.dao.recordDismissal("1", "root", 2_000)
        f.dao.recordTerminal("1", "root", AlarmOccurrenceOutboxKind.FORCE_ENDED, 3_000)
        f.remote.beforeEvent = { if (f.remote.events.size == 1) throw ApiException(503, "UNAVAILABLE", "재시도") }
        assertEquals(15_000L, f.syncer.flush())
        assertEquals(listOf("POST", "InitialDismissed"), f.remote.calls)
        f.time = 15_000
        f.syncer.flush()
        assertEquals(listOf("POST", "InitialDismissed", "InitialDismissed", "ForceEnded"), f.remote.calls)
        assertEquals(f.remote.events[0], f.remote.events[1])
    }

    @Test
    fun `필수 시각이 없는 실행은 보류하고 다른 실행은 계속 전송한다`() = withFixture { f ->
        f.start(startedAt = null)
        f.start(id = "other", startedAt = 2_000)
        f.syncer.flush()
        assertEquals(AlarmOccurrenceOutboxState.BLOCKED, f.dao.getEvents("1", "root").single().state)
        assertEquals("MISSING_TIME", f.dao.getEvents("1", "root").single().lastErrorCode)
        assertEquals(1, f.remote.starts.size)
        f.start(startedAt = 1_000)
        f.syncer.flush()
        assertEquals(2, f.remote.starts.size)
        assertTrue(f.dao.getUnsentEvents("1").isEmpty())
    }

    @Test
    fun `영구 오류는 자동 재시도하지 않는다`() = withFixture { f ->
        f.start()
        f.remote.beforeStart = { throw ApiException(400, "INVALID_TIME", "요청 검증 실패") }
        assertNull(f.syncer.flush())
        f.time += 1_000_000
        f.syncer.flush()
        assertEquals(1, f.remote.calls.size)
        assertEquals("INVALID_TIME", f.dao.getEvents("1", "root").single().lastErrorCode)
    }

    @Test
    fun `계정이 응답 도중 바뀌면 이전 계정 이벤트를 완료 처리하거나 새 계정으로 보내지 않는다`() = withFixture { f ->
        f.start()
        f.remote.beforeStart = {
            f.tokens.tokens = AuthTokens(token(2), "refresh")
            f.session.startNewSession()
        }
        f.syncer.flush()
        assertNull(f.dao.getExecution("1", "root")?.serverOccurrenceId)
        assertEquals(AlarmOccurrenceOutboxState.PENDING, f.dao.getEvents("1", "root").single().state)
        f.syncer.flush()
        assertEquals(1, f.remote.calls.size)
        f.tokens.tokens = AuthTokens(token(1), "refresh")
        f.session.startNewSession()
        f.remote.beforeStart = {}
        f.syncer.flush()
        assertTrue(f.dao.getUnsentEvents("1").isEmpty())
    }

    @Test
    fun `인증 실패는 같은 세션에서 반복하지 않고 재로그인 후 재개한다`() = withFixture { f ->
        f.start()
        f.remote.beforeStart = { throw ApiException(401, "COMMON401", "로그인 필요") }
        f.syncer.flush()
        f.syncer.flush()
        assertEquals(1, f.remote.calls.size)
        assertEquals("AUTH_REQUIRED", f.dao.getEvents("1", "root").single().lastErrorCode)
        f.remote.beforeStart = {}
        f.session.startNewSession()
        f.syncer.flush()
        assertTrue(f.dao.getUnsentEvents("1").isEmpty())
    }

    @Test
    fun `여러 전송기가 동시에 실행되어도 한 이벤트를 중복 전송하지 않는다`() = withFixture { f ->
        f.start()
        coroutineScope { listOf(async { f.syncer.flush() }, async { f.newSyncer().flush() }).awaitAll() }
        assertEquals(1, f.remote.calls.size)
    }

    @Test
    fun `취소된 요청은 실패로 덮어쓰지 않고 다음 실행에서 다시 보낼 수 있다`() = withFixture { f ->
        f.start()
        val entered = CompletableDeferred<Unit>()
        f.remote.beforeStart = { entered.complete(Unit); CompletableDeferred<Unit>().await() }
        coroutineScope {
            val request = async { f.syncer.flush() }
            entered.await()
            request.cancelAndJoin()
        }
        assertEquals(AlarmOccurrenceOutboxState.PENDING, f.dao.getEvents("1", "root").single().state)
        assertNull(f.dao.getEvents("1", "root").single().lastErrorCode)
        f.remote.beforeStart = {}
        f.syncer.flush()
        assertTrue(f.dao.getUnsentEvents("1").isEmpty())
    }

    @Test
    fun `종료 전 대기 중인 해제를 먼저 보내고 종료 이후 추가 이벤트는 보류한다`() = withFixture { f ->
        f.start()
        f.dao.recordTerminal("1", "root", AlarmOccurrenceOutboxKind.ARRIVED, 3_000)
        f.dao.recordDismissal("1", "root", 2_000)
        f.syncer.flush()
        assertEquals(listOf("POST", "InitialDismissed", "Arrived"), f.remote.calls)
        f.dao.recordRepeat("1", "root", "late", "repeat-id", 4_000)
        f.syncer.flush()
        assertEquals(3, f.remote.calls.size)
        assertEquals("ALREADY_ENDED", f.dao.getEvents("1", "root").last().lastErrorCode)
    }

    @Test
    fun `실행 루프는 오프라인에서 기다리고 연결 복구 및 새 이벤트 저장 시 전송한다`() = withFixture { f ->
        f.start()
        val status = MutableStateFlow(NetworkStatus.Offline)
        val monitor = object : NetworkMonitor { override val status = status }
        coroutineScope {
            val job = AlarmOccurrenceSyncCoordinator(f.syncer, f.dao, f.session, monitor, { f.time }).start(this)
            try {
                delay(100)
                assertTrue(f.remote.calls.isEmpty())
                status.value = NetworkStatus.Online
                withTimeout(5_000) { f.dao.observeUnsentEvents().first { it.isEmpty() } }
                f.dao.recordDismissal("1", "root", 2_000)
                withTimeout(5_000) { f.dao.observeUnsentEvents().first { it.isEmpty() } }
                assertEquals(listOf("POST", "InitialDismissed"), f.remote.calls)
            } finally { job.cancelAndJoin() }
        }
    }
}

private fun withFixture(block: suspend (SyncFixture) -> Unit) = runBlocking {
    val f = SyncFixture()
    try { block(f) } finally { f.client.close(); f.db.close() }
}

private class SyncFixture {
    val db = Room.inMemoryDatabaseBuilder<RingoutDatabase>().setDriver(BundledSQLiteDriver()).build()
    val dao = db.alarmOccurrenceSyncDao()
    val client = HttpClient(MockEngine { error("가짜 Repository만 호출해야 합니다.") })
    val session = AuthSession().apply { startNewSession() }
    val tokens = SyncTokens()
    val remote = SyncRemote()
    var time = 10_000L
    var readTime: () -> Long = { time }
    val syncer = newSyncer()
    fun newSyncer() = AlarmOccurrenceSyncer(dao, client, tokens, session, { readTime() }, { remote })
    suspend fun start(id: String = "root", startedAt: Long? = 1_000) =
        dao.recordStart(AlarmOccurrenceSyncEntity(id, "1", "alarm-$id", 1, 900, startedAt))
}

private class SyncRemote : AlarmOccurrenceRepository {
    val calls = mutableListOf<String>()
    val starts = mutableListOf<AlarmOccurrenceStart>()
    val events = mutableListOf<AlarmOccurrenceEvent>()
    var beforeStart: suspend () -> Unit = {}
    var beforeEvent: suspend () -> Unit = {}
    override suspend fun start(start: AlarmOccurrenceStart): AlarmOccurrence {
        calls += "POST"
        starts += start
        beforeStart()
        return response(if (start.alarmId == "alarm-root") ServerId else OtherServerId)
    }
    override suspend fun recordEvent(occurrenceId: AlarmOccurrenceId, event: AlarmOccurrenceEvent): AlarmOccurrence {
        calls += when (event) {
            is AlarmOccurrenceEvent.InitialDismissed -> "InitialDismissed"
            is AlarmOccurrenceEvent.RepeatRang -> "RepeatRang"
            is AlarmOccurrenceEvent.RepeatDismissed -> "RepeatDismissed"
            is AlarmOccurrenceEvent.Arrived -> "Arrived"
            is AlarmOccurrenceEvent.ForceEnded -> "ForceEnded"
        }
        events += event
        beforeEvent()
        return response(occurrenceId.value)
    }
}

private class SyncTokens : SecureTokenStorage {
    var tokens: AuthTokens? = AuthTokens(token(1), "refresh")
    override suspend fun read() = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}

private fun token(id: Int) = "header." + Base64.UrlSafe.encode("""{"sub":"$id","userId":$id,"tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=') + ".signature"
private const val ServerId = "5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f"
private const val OtherServerId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
private fun response(id: String) = AlarmOccurrence(AlarmOccurrenceId(id), Instant.fromEpochMilliseconds(1_000), emptyList(), null, null)
