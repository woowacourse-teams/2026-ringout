package com.joon.ringout.data.alarmoccurrence

import androidx.room3.Room
import androidx.sqlite.execSQL
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import com.joon.ringout.data.database.createMigrationTestDatabasePath
import com.joon.ringout.data.database.createMigrationTestSQLiteDriver
import com.joon.ringout.data.database.deleteMigrationTestDatabaseFiles
import com.joon.ringout.data.forest.ForestArrivalPlanting
import com.joon.ringout.data.forest.ForestArrivalPlantingGate
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrence
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceEvent
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceId
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceRepository
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceStart
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The same Room-backed arrival/forest handoff contract runs on Android and iOS. */
class ForestArrivalPlantingSyncTestSuite {
    fun `도착마다 숲 판정을 끝내서 서버 성공일이 사일에서 육일로 바뀌어도 첫 나무를 놓치지 않는다`() = withPlantingFixture { f ->
        f.arrival(1, 5_000)
        f.arrival(2, 6_000)

        assertNull(f.syncer().flush())

        assertEquals(listOf("arrival:1", "checkpoint:1", "forest:5", "arrival:2", "checkpoint:2", "forest:6"), f.trace.filter { !it.startsWith("start:") })
        assertEquals(mapOf(1 to 5_000L), f.planting.plannedTrees)
        assertTrue(f.dao.getUnsentEvents("1").isEmpty())
    }

    fun `숲 조회 대기 중에는 다음 도착만 보류하고 울림 해제와 강제 종료는 전송한다`() = withPlantingFixture { f ->
        f.arrival(1, 5_000)
        f.arrival(2, 6_000)
        f.dao.recordDismissal("1", "execution-2", 4_000)
        f.start(3)
        f.dao.recordTerminal("1", "execution-3", AlarmOccurrenceOutboxKind.FORCE_ENDED, 7_000)
        f.planting.gate = ForestArrivalPlantingGate(false, 15_000)

        assertEquals(15_000L, f.syncer().flush())

        assertEquals(listOf(1), f.remote.arrivalIds)
        assertTrue("dismiss:2" in f.trace && "force:3" in f.trace)
        assertEquals(AlarmOccurrenceOutboxState.SENT, f.arrivalState(1))
        assertEquals(AlarmOccurrenceOutboxState.PENDING, f.arrivalState(2))
        f.now = 15_000
        f.planting.gate = null
        assertNull(f.syncer().flush())
        assertEquals(listOf(1, 2), f.remote.arrivalIds)
        assertEquals(mapOf(1 to 5_000L), f.planting.plannedTrees)
    }

    fun `실행 저장 순서와 달라도 실제 도착 시각이 이른 기록부터 전송한다`() = withPlantingFixture { f ->
        f.arrival(1, 6_000)
        f.arrival(2, 5_000)

        f.syncer().flush()

        assertEquals(listOf(2, 1), f.remote.arrivalIds)
        assertEquals(mapOf(1 to 5_000L), f.planting.plannedTrees)
    }

    fun `앞선 도착이 네트워크 재시도 중이면 다음 도착이 추월하지 않는다`() = withPlantingFixture { f ->
        f.arrival(1, 6_000)
        f.arrival(2, 5_000)
        f.remote.beforeEvent = { id, event ->
            if (id == 2 && event is AlarmOccurrenceEvent.Arrived) throw ApiException(503, "TEMPORARY", "retry")
        }

        assertEquals(15_000L, f.syncer().flush())
        assertEquals(listOf(2), f.remote.arrivalIds)
        assertEquals(AlarmOccurrenceOutboxState.PENDING, f.arrivalState(1))
        f.syncer().flush()
        assertEquals(listOf(2), f.remote.arrivalIds)
        f.now = 15_000
        f.remote.beforeEvent = { _, _ -> }
        f.syncer().flush()
        assertEquals(listOf(2, 2, 1), f.remote.arrivalIds)
        assertEquals(mapOf(1 to 5_000L), f.planting.plannedTrees)
    }

    fun `획득 판단 기록 저장이 실패하면 도착을 완료 표시하지 않고 동일 시각으로 복구한다`() = withPlantingFixture { f ->
        f.arrival(1, 5_000)
        f.planting.beforeRecord = { _, _ -> throw IOException("disk unavailable") }

        assertFailsWith<IOException> { f.syncer().flush() }
        assertEquals(AlarmOccurrenceOutboxState.PENDING, f.arrivalState(1))
        assertNull(f.dao.getEvents("1", "execution-1").last().lastErrorCode)
        assertTrue(f.planting.checkpoints.isEmpty())
        f.planting.beforeRecord = { _, _ -> assertEquals(AlarmOccurrenceOutboxState.PENDING, f.arrivalState(1)) }
        f.syncer().flush()
        assertEquals(listOf(5_000L, 5_000L), f.remote.arrivalTimes)
        assertEquals(1, f.planting.checkpoints.size)
        assertEquals(mapOf(1 to 5_000L), f.planting.plannedTrees)
        assertEquals(AlarmOccurrenceOutboxState.SENT, f.arrivalState(1))
    }

    fun `판단 기록 저장 뒤 완료 표시가 실패해도 재실행에서 같은 획득을 한 번만 처리한다`() = withPlantingFixture { f ->
        f.arrival(1, 5_000)
        f.sql("""CREATE TRIGGER reject_arrival_sent BEFORE UPDATE OF state ON alarm_occurrence_outbox
            WHEN NEW.kind = 'ARRIVED' AND NEW.state = 'SENT'
            BEGIN SELECT RAISE(ABORT, 'disk write failed'); END""")

        assertFailsWith<Exception> { f.syncer().flush() }
        assertEquals(1, f.planting.checkpoints.size)
        assertEquals(AlarmOccurrenceOutboxState.PENDING, f.arrivalState(1))
        f.sql("DROP TRIGGER reject_arrival_sent")
        f.syncer().flush()
        assertEquals(listOf(5_000L, 5_000L), f.remote.arrivalTimes)
        assertEquals(1, f.trace.count { it == "forest:5" })
        assertEquals(mapOf(1 to 5_000L), f.planting.plannedTrees)
        assertEquals(AlarmOccurrenceOutboxState.SENT, f.arrivalState(1))
    }

    fun `도착 성공 시각이 없거나 다른 종료와 시각을 반환하면 식재를 시작하지 않는다`() {
        val changes: List<(AlarmOccurrence) -> AlarmOccurrence> = listOf(
            { it.copy(arrivedAt = null) },
            { it.copy(forceEndedAt = Instant.fromEpochMilliseconds(5_000)) },
            { it.copy(arrivedAt = Instant.fromEpochMilliseconds(5_001)) },
        )
        changes.forEach { change ->
            withPlantingFixture { f ->
                f.arrival(1, 5_000)
                f.remote.changeResponse = change
                f.syncer().flush()
                assertEquals(AlarmOccurrenceOutboxState.BLOCKED, f.arrivalState(1))
                assertEquals("INVALID_RESPONSE", f.dao.getEvents("1", "execution-1").last().lastErrorCode)
                assertTrue(f.planting.checkpoints.isEmpty())
                assertTrue(f.planting.plannedTrees.isEmpty())
            }
        }
    }

    fun `도착 응답 도중 계정이 바뀌면 이전 응답으로 획득을 기록하지 않는다`() = withPlantingFixture { f ->
        f.arrival(1, 5_000)
        f.remote.beforeEvent = { _, event ->
            if (event is AlarmOccurrenceEvent.Arrived) {
                f.tokens.value = AuthTokens(plantingToken(2), "refresh")
                f.session.startNewSession()
            }
        }

        f.syncer().flush()
        assertTrue(f.planting.checkpoints.isEmpty())
        assertEquals(AlarmOccurrenceOutboxState.PENDING, f.arrivalState(1))
        f.syncer().flush()
        assertEquals(listOf(1), f.remote.arrivalIds)
        f.tokens.value = AuthTokens(plantingToken(1), "refresh")
        f.session.startNewSession()
        f.remote.beforeEvent = { _, _ -> }
        f.syncer().flush()
        assertEquals(mapOf(1 to 5_000L), f.planting.plannedTrees)
    }

    fun `도착 전송 대기열이 비어도 실행 루프가 남은 숲 판정을 복구하고 수동 재시도에 반응한다`() = withPlantingFixture { f ->
        f.arrival(1, 5_000)
        f.planting.gate = ForestArrivalPlantingGate(false)
        f.syncer().flush()
        assertTrue(f.dao.getUnsentEvents("1").isEmpty())
        val attempts = f.planting.resolutionAttempts.value
        val monitor = object : NetworkMonitor { override val status = MutableStateFlow(NetworkStatus.Online) }
        coroutineScope {
            val job = AlarmOccurrenceSyncCoordinator(f.syncer(), f.dao, f.session, monitor, { f.now }).start(this)
            try {
                withTimeout(5_000) { f.planting.resolutionAttempts.first { it > attempts } }
                f.planting.gate = null
                f.planting.workRevision.value++
                withTimeout(5_000) { f.planting.resolved.first { plantingServerId(1) in it } }
                assertEquals(mapOf(1 to 5_000L), f.planting.plannedTrees)
                assertEquals(listOf(1), f.remote.arrivalIds)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    fun `기존에 전송을 마친 도착은 식재 판단 기록으로 소급 이관하지 않는다`() = withPlantingFixture { f ->
        f.arrival(1, 5_000)
        f.syncer(ForestArrivalPlanting.None).flush()
        assertTrue(f.dao.getUnsentEvents("1").isEmpty())

        f.syncer().flush()

        assertTrue(f.planting.checkpoints.isEmpty())
        assertTrue(f.planting.plannedTrees.isEmpty())
        assertFalse(f.trace.any { it.startsWith("forest:") })
    }

    fun `숲 판정의 저장 오류는 다른 실행의 강제 종료를 막거나 도착을 잘못된 응답으로 바꾸지 않는다`() = withPlantingFixture { f ->
        f.arrival(1, 5_000)
        f.arrival(2, 6_000)
        f.start(3)
        f.dao.recordTerminal("1", "execution-3", AlarmOccurrenceOutboxKind.FORCE_ENDED, 7_000)
        f.planting.beforeResolve = { throw IOException("state read failed") }

        assertFailsWith<IOException> { f.syncer().flush() }
        assertTrue("force:3" in f.trace)
        assertEquals(AlarmOccurrenceOutboxState.PENDING, f.arrivalState(1))
        assertEquals(AlarmOccurrenceOutboxState.PENDING, f.arrivalState(2))
        assertTrue(f.dao.getEvents("1", "execution-1").none { it.lastErrorCode == "INVALID_RESPONSE" })
        f.planting.beforeResolve = {}
        f.syncer().flush()
        assertEquals(listOf(1, 2), f.remote.arrivalIds)
        assertEquals(mapOf(1 to 5_000L), f.planting.plannedTrees)
    }

    fun `여러 전송기가 동시에 실행되어도 도착과 획득 판단을 중복하지 않는다`() = withPlantingFixture { f ->
        f.arrival(1, 5_000)
        coroutineScope { listOf(async { f.syncer().flush() }, async { f.syncer().flush() }).awaitAll() }
        assertEquals(listOf(1), f.remote.arrivalIds)
        assertEquals(1, f.trace.count { it == "forest:5" })
        assertEquals(mapOf(1 to 5_000L), f.planting.plannedTrees)
    }
}

private fun withPlantingFixture(block: suspend (PlantingArrivalFixture) -> Unit) = runBlocking {
    val fixture = PlantingArrivalFixture()
    try { block(fixture) } finally { fixture.close() }
}

private class PlantingArrivalFixture {
    private val path = createMigrationTestDatabasePath()
    val database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path), driver = createMigrationTestSQLiteDriver())
    val dao = database.alarmOccurrenceSyncDao()
    val client = HttpClient(MockEngine { error("Only the fake occurrence repository may be called") })
    val session = AuthSession().apply { startNewSession() }
    val tokens = PlantingArrivalTokens()
    val trace = mutableListOf<String>()
    val remote = PlantingArrivalRemote(trace)
    val planting = PlantingArrivalGateFake(remote, trace)
    var now = 10_000L
    fun syncer(gate: ForestArrivalPlanting = planting) = AlarmOccurrenceSyncer(
        dao, client, tokens, session, { now }, { remote }, roomsEnabled = false, forestPlanting = gate,
    )
    suspend fun start(id: Int) = dao.recordStart(AlarmOccurrenceSyncEntity("execution-$id", "1", "alarm-$id", 1, 900, 1_000))
    suspend fun arrival(id: Int, at: Long) {
        start(id)
        dao.recordTerminal("1", "execution-$id", AlarmOccurrenceOutboxKind.ARRIVED, at)
    }
    suspend fun arrivalState(id: Int) = dao.getEvents("1", "execution-$id").single { it.kind == AlarmOccurrenceOutboxKind.ARRIVED }.state
    fun sql(statement: String) = createMigrationTestSQLiteDriver().open(path).use { it.execSQL(statement) }
    fun close() {
        client.close()
        database.close()
        deleteMigrationTestDatabaseFiles(path)
    }
}

private class PlantingArrivalGateFake(private val remote: PlantingArrivalRemote, private val trace: MutableList<String>) : ForestArrivalPlanting {
    val checkpoints = linkedMapOf<String, Long>()
    val resolved = MutableStateFlow<Set<String>>(emptySet())
    val plannedTrees = mutableMapOf<Int, Long>()
    val resolutionAttempts = MutableStateFlow(0)
    val workRevision = MutableStateFlow(0)
    var gate: ForestArrivalPlantingGate? = null
    var beforeRecord: suspend (String, Long) -> Unit = { _, _ -> }
    var beforeResolve: suspend () -> Unit = {}
    override suspend fun recordConfirmedArrival(account: AlarmOccurrenceAccount, occurrenceId: String, arrivedAtEpochMillis: Long) {
        beforeRecord(occurrenceId, arrivedAtEpochMillis)
        if (occurrenceId !in checkpoints) {
            checkpoints[occurrenceId] = arrivedAtEpochMillis
            trace += "checkpoint:${occurrenceId.takeLast(1)}"
            workRevision.value++
        }
    }
    override suspend fun resolvePendingArrivals(account: AlarmOccurrenceAccount, now: () -> Long): ForestArrivalPlantingGate {
        beforeResolve()
        for ((id, at) in checkpoints.filterKeys { it !in resolved.value }) {
            resolutionAttempts.value++
            gate?.let { return it }
            trace += "forest:${remote.successfulDays}"
            if (remote.successfulDays in 5..80 && remote.successfulDays % 5 == 0) {
                plannedTrees.getOrPut(remote.successfulDays / 5) { at }
            }
            resolved.value += id
        }
        return ForestArrivalPlantingGate(true)
    }
    override fun observeArrivalPlantingWork() = workRevision.map { Unit }
}

private class PlantingArrivalRemote(private val trace: MutableList<String>) : AlarmOccurrenceRepository {
    var successfulDays = 4
    val arrivalIds = mutableListOf<Int>()
    val arrivalTimes = mutableListOf<Long>()
    private val acceptedArrivals = mutableSetOf<Int>()
    var beforeEvent: suspend (Int, AlarmOccurrenceEvent) -> Unit = { _, _ -> }
    var changeResponse: (AlarmOccurrence) -> AlarmOccurrence = { it }
    override suspend fun start(start: AlarmOccurrenceStart): AlarmOccurrence {
        val id = start.alarmId.removePrefix("alarm-").toInt()
        trace += "start:$id"
        return response(id)
    }
    override suspend fun recordEvent(occurrenceId: AlarmOccurrenceId, event: AlarmOccurrenceEvent): AlarmOccurrence {
        val id = occurrenceId.value.takeLast(1).toInt()
        if (event is AlarmOccurrenceEvent.Arrived) {
            arrivalIds += id
            arrivalTimes += event.arrivedAt.toEpochMilliseconds()
            trace += "arrival:$id"
        } else trace += when (event) {
            is AlarmOccurrenceEvent.ForceEnded -> "force:$id"
            else -> "dismiss:$id"
        }
        beforeEvent(id, event)
        if (event is AlarmOccurrenceEvent.Arrived && acceptedArrivals.add(id)) successfulDays++
        return changeResponse(response(id).copy(
            arrivedAt = (event as? AlarmOccurrenceEvent.Arrived)?.arrivedAt,
            forceEndedAt = (event as? AlarmOccurrenceEvent.ForceEnded)?.forceEndedAt,
        ))
    }
    private fun response(id: Int) = AlarmOccurrence(AlarmOccurrenceId(plantingServerId(id)), Instant.fromEpochMilliseconds(1_000), emptyList(), null, null)
}

private class PlantingArrivalTokens : SecureTokenStorage {
    var value: AuthTokens? = AuthTokens(plantingToken(1), "refresh")
    override suspend fun read() = value
    override suspend fun save(tokens: AuthTokens) { value = tokens }
    override suspend fun clear() { value = null }
}

private fun plantingServerId(id: Int) = "00000000-0000-0000-0000-" + id.toString().padStart(12, '0')
private fun plantingToken(id: Int) = "header." + Base64.UrlSafe.encode("""{"sub":"$id","userId":$id,"tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=') + ".signature"
