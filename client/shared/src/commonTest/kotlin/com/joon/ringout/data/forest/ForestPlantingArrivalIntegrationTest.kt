package com.joon.ringout.data.forest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room3.Room
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxKind
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxState
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncEntity
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncer
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import com.joon.ringout.data.database.createMigrationTestDatabasePath
import com.joon.ringout.data.database.createMigrationTestSQLiteDriver
import com.joon.ringout.data.database.deleteMigrationTestDatabaseFiles
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrence
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceEvent
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceId
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceRepository
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceStart
import com.joon.ringout.domain.alarmoccurrence.alarmOccurrenceServerDate
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.forest.ForestTreeKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The same arrival outbox → forest GET → durable PUT path runs on both SQLite drivers. */
class ForestPlantingArrivalIntegrationTestSuite {
    fun `재설치한 기기의 도착 하나로 서버의 다섯째 성공일 나무를 온보딩 전에 저장한다`() = runBlocking {
        withFixture { fixture ->
            fixture.arrival("new-phone", FifthAt)
            assertNull(fixture.syncer.flush())

            assertEquals(1, fixture.dao.observeSuccessfulArrivalSyncs("1").first())
            assertEquals(listOf("ARRIVED:5", "GET:5"), fixture.events)
            val pending = fixture.store.read("1").treePlantingDeliveries.getValue(1)
            assertEquals(FifthAt, pending.plantedAtEpochMillis)
            assertTrue(fixture.repository.getForest().trees.isEmpty())
            assertNull(fixture.repository.getForest().onboardingCompletedAtEpochMillis)

            fixture.repository.syncForest { fixture.now }

            assertEquals(listOf("ARRIVED:5", "GET:5", "PUT:1"), fixture.events)
            val tree = fixture.repository.getForest().trees.single()
            assertEquals(1, tree.growthCycle)
            assertEquals(ForestTreeKind.Oak, tree.kind)
            assertEquals(7, tree.slotIndex)
            assertEquals(FifthAt, tree.plantedAtEpochMillis)
            assertNull(tree.acknowledgedAtEpochMillis)
            assertEquals(ForestTreePlantingDeliveryStatus.Synced, fixture.store.read("1").treePlantingDeliveries[1]?.status)
            assertEquals(setOf("kind", "plantedAt"), fixture.putBodies.single().keys)
        }
    }

    fun `오프라인 도착을 시각순으로 조회하여 여섯째 날 전송 뒤에도 다섯째 날 나무를 저장한다`() = runBlocking {
        withFixture { fixture ->
            fixture.arrival("sixth-created-first", SixthAt)
            fixture.arrival("fifth-created-later", FifthAt)

            assertNull(fixture.syncer.flush())

            assertEquals(listOf("ARRIVED:5", "GET:5", "ARRIVED:6", "GET:6"), fixture.events)
            assertEquals(FifthAt, fixture.store.read("1").treePlantingDeliveries.getValue(1).plantedAtEpochMillis)
            fixture.repository.syncForest { fixture.now }
            assertEquals(6, fixture.repository.getForest().growth.successfulDayCount)
            assertEquals(FifthAt, fixture.repository.getForest().trees.single().plantedAtEpochMillis)
            val requests = fixture.events.toList()
            fixture.syncer.flush()
            fixture.repository.syncForest { fixture.now }
            assertEquals(requests, fixture.events)
        }
    }

    fun `획득 조회 실패 뒤 다음 도착은 기다리고 강제 종료는 전송한 뒤 재생성한 저장소로 복구한다`() = runBlocking {
        withFixture { fixture ->
            fixture.arrival("fifth", FifthAt)
            fixture.arrival("sixth", SixthAt)
            fixture.arrival("forced", SixthAt + 1, AlarmOccurrenceOutboxKind.FORCE_ENDED)
            fixture.failNextGet = true

            assertEquals(5_000L, fixture.syncer.flush())
            assertEquals(1, fixture.dao.observeSuccessfulArrivalSyncs("1").first())
            assertTrue(fixture.events.contains("FORCE_ENDED"))
            assertFalse(fixture.events.contains("ARRIVED:6"))
            assertEquals(AlarmOccurrenceOutboxState.PENDING, fixture.dao.getEvents("1", "sixth").single { it.kind == AlarmOccurrenceOutboxKind.ARRIVED }.state)

            fixture.recreateForest()
            fixture.now = 4_999
            fixture.syncer.flush()
            assertFalse(fixture.events.contains("ARRIVED:6"))
            fixture.now = 5_000
            assertNull(fixture.syncer.flush())
            fixture.repository.syncForest { fixture.now }

            assertEquals(2, fixture.dao.observeSuccessfulArrivalSyncs("1").first())
            assertEquals(FifthAt, fixture.repository.getForest().trees.single().plantedAtEpochMillis)
            assertEquals(1, fixture.events.count { it == "PUT:1" })
        }
    }

    private suspend fun withFixture(block: suspend (PlantingArrivalFixture) -> Unit) {
        val path = createMigrationTestDatabasePath()
        val database = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path), createMigrationTestSQLiteDriver())
        val fixture = PlantingArrivalFixture(database)
        try {
            withTimeout(30_000) { block(fixture) }
        } finally {
            fixture.close()
            database.close()
            deleteMigrationTestDatabaseFiles(path)
        }
    }
}

private class PlantingArrivalFixture(database: RingoutDatabase) {
    val dao = database.alarmOccurrenceSyncDao()
    private val preferences = PlantingArrivalPreferences()
    val store = DataStoreForestLocalProgressStore(preferences)
    private val tokens = PlantingArrivalTokens()
    private val session = AuthSession().apply { startNewSession() }
    var now = 0L
    var failNextGet = false
    val events = mutableListOf<String>()
    val putBodies = mutableListOf<JsonObject>()
    private val successfulDates = mutableSetOf<String>()
    private val serverArrivals = mutableMapOf<String, Instant>()
    private var serial = 0
    private var serverTree: String? = null
    private val occurrenceRepository = object : AlarmOccurrenceRepository {
        override suspend fun start(start: AlarmOccurrenceStart): AlarmOccurrence = error("선행 START는 준비 단계에서 확정합니다.")
        override suspend fun recordEvent(occurrenceId: AlarmOccurrenceId, event: AlarmOccurrenceEvent): AlarmOccurrence = when (event) {
            is AlarmOccurrenceEvent.Arrived -> {
                val at = serverArrivals.getOrPut(occurrenceId.value) { event.arrivedAt }
                successfulDates += alarmOccurrenceServerDate(at).iso8601
                events += "ARRIVED:${4 + successfulDates.size}"
                AlarmOccurrence(occurrenceId, at, emptyList(), at, null)
            }
            is AlarmOccurrenceEvent.ForceEnded -> {
                events += "FORCE_ENDED"
                AlarmOccurrence(occurrenceId, event.forceEndedAt, emptyList(), null, event.forceEndedAt)
            }
            else -> error("예상하지 않은 알람 이벤트")
        }
    }
    private val client = HttpClient(MockEngine { request ->
        assertEquals("Bearer ${tokens.read()!!.accessToken}", request.headers[HttpHeaders.Authorization])
        when (request.method) {
            HttpMethod.Get -> {
                assertEquals("/api/v1/forest", request.url.encodedPath)
                val count = 4 + successfulDates.size
                events += "GET:$count"
                if (failNextGet) {
                    failNextGet = false
                    respond("""{"isSuccess":false,"code":"FOREST500","message":"retry","result":null}""", HttpStatusCode.InternalServerError, PlantingHeaders)
                } else respond("""{"isSuccess":true,"code":"FOREST200","message":"ok","result":{"growth":{"successfulDayCount":$count,"filledSlots":${count % 5},"remainingDays":${5 - count % 5},"isComplete":false},"onboardingCompletedAt":null,"trees":[${serverTree.orEmpty()}]}}""", headers = PlantingHeaders)
            }
            HttpMethod.Put -> {
                assertEquals("/api/v1/forest/trees/1", request.url.encodedPath)
                assertTrue(request.body.contentType.toString().startsWith("application/json"))
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                putBodies += body
                events += "PUT:1"
                val at = body.getValue("plantedAt").jsonPrimitive.content
                serverTree = """{"growthCycle":1,"kind":"oak","slotIndex":7,"plantedAt":"$at","acknowledgedAt":null}"""
                respond("""{"isSuccess":true,"code":"FOREST201","message":"ok","result":$serverTree}""", HttpStatusCode.Created, PlantingHeaders)
            }
            else -> error("온보딩을 완료하지 않은 사용자는 GET과 나무 PUT만 전송해야 합니다.")
        }
    }) { configureRingoutHttpClient() }
    var repository = createForest()
        private set
    var syncer = createSyncer()
        private set

    private fun createForest() = DefaultForestRepository(client, tokens, session, DataStoreForestLocalProgressStore(preferences), dao::observeSuccessfulArrivalSyncs)
    private fun createSyncer() = AlarmOccurrenceSyncer(
        dao, client, tokens, session, now = { now }, repositoryFactory = { occurrenceRepository }, roomsEnabled = false,
        forestPlanting = repository,
    )

    fun recreateForest() {
        repository = createForest()
        syncer = createSyncer()
    }

    suspend fun arrival(id: String, at: Long, kind: AlarmOccurrenceOutboxKind = AlarmOccurrenceOutboxKind.ARRIVED) {
        dao.recordStart(AlarmOccurrenceSyncEntity(id, "1", "alarm-$id", 1, at - 60_000, at - 60_000))
        dao.completeStart("1", id, "00000000-0000-0000-0000-${(++serial).toString().padStart(12, '0')}")
        dao.recordTerminal("1", id, kind, at, roomsEnabled = false)
    }

    fun close() = client.close()
}

private class PlantingArrivalPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        transform(data.value).also { data.value = it }
    }
}

private class PlantingArrivalTokens : SecureTokenStorage {
    private val claims = Base64.UrlSafe.encode("""{"userId":1,"sub":"1","tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=')
    private var tokens: AuthTokens? = AuthTokens("header.$claims.signature", "refresh")
    override suspend fun read() = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}

private val PlantingHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private val FifthAt = Instant.parse("2026-11-10T08:00:00+09:00").toEpochMilliseconds()
private val SixthAt = FifthAt + 86_400_000L
