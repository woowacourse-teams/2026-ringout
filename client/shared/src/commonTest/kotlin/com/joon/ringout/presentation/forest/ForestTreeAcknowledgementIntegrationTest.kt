package com.joon.ringout.presentation.forest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.joon.ringout.data.forest.DataStoreForestLocalProgressStore
import com.joon.ringout.data.forest.DefaultForestRepository
import com.joon.ringout.data.forest.ForestSyncCoordinator
import com.joon.ringout.data.forest.ForestTreeAcknowledgementDeliveryStatus
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ForestTreeAcknowledgementIntegrationTest {
    @Test
    fun `나무 확인은 서버 응답 전에 웹에 성공하고 화면 종료 뒤에도 서버 시각을 저장한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fixture = TreeAcknowledgementFixture(this) {
            gate.await()
            respond(TreeAcknowledgementSuccess, headers = TreeAcknowledgementHeaders)
        }
        try {
            fixture.open()
            runCurrent()
            fixture.acknowledge()
            runCurrent()

            fixture.assertBridgeAcknowledged()
            assertEquals(1, fixture.patchBodies.size)
            assertEquals(ForestTreeAcknowledgementDeliveryStatus.Pending, fixture.deliveryStatus())
            fixture.closePage()
            val messageCount = fixture.messages.size

            gate.complete(Unit)
            runCurrent()

            val restored = DataStoreForestLocalProgressStore(fixture.preferences).read("1")
            assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, restored.treeAcknowledgementDeliveries.values.single().status)
            assertEquals(TreeServerAt, restored.acknowledgedTrees.values.single())
            assertEquals(messageCount, fixture.messages.size)
            val forest = fixture.repository.getForest()
            assertEquals(12, forest.growth.successfulDayCount)
            assertNull(forest.trees.single { it.growthCycle == 2 }.acknowledgedAtEpochMillis)
            assertEquals(1, fixture.getCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `나무 전송 실패 뒤에도 연출 확인을 유지하고 화면 밖에서 같은 시각으로 자동 재시도한다`() = runTest {
        val fixture = TreeAcknowledgementFixture(this) { attempt ->
            if (attempt == 1) respond(
                """{"isSuccess":false,"code":"FOREST500","message":"retry","result":null}""",
                HttpStatusCode.InternalServerError,
                TreeAcknowledgementHeaders,
            ) else respond(TreeAcknowledgementSuccess, headers = TreeAcknowledgementHeaders)
        }
        try {
            fixture.open()
            runCurrent()
            fixture.acknowledge()
            runCurrent()

            fixture.assertBridgeAcknowledged()
            assertEquals(TreeFirstAt, fixture.repository.getForest().trees.first().acknowledgedAtEpochMillis)
            assertEquals(ForestTreeAcknowledgementDeliveryStatus.Pending, fixture.deliveryStatus())
            assertEquals(5_000L, fixture.store.read("1").treeAcknowledgementDeliveries.values.single().nextRetryAtEpochMillis)
            fixture.closePage()

            advanceTimeBy(4_999)
            runCurrent()
            assertEquals(1, fixture.patchBodies.size)
            advanceTimeBy(1)
            runCurrent()

            assertEquals(listOf(TreeFirstAt, TreeFirstAt).map { Instant.fromEpochMilliseconds(it).toString() }, fixture.patchBodies)
            assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, fixture.deliveryStatus())
            assertEquals(TreeServerAt, fixture.repository.getForest().trees.first().acknowledgedAtEpochMillis)
            assertEquals(1, fixture.getCount)
        } finally {
            fixture.close()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TreeAcknowledgementFixture(
    scope: TestScope,
    patchHandler: suspend MockRequestHandleScope.(Int) -> HttpResponseData,
) {
    val preferences = TreeAcknowledgementPreferences()
    val store = DataStoreForestLocalProgressStore(preferences)
    private val session = AuthSession().apply { startNewSession() }
    private val tokens = TreeAcknowledgementTokens()
    val patchBodies = mutableListOf<String>()
    var getCount = 0
    private val client = HttpClient(MockEngine(MockEngineConfig().apply {
        dispatcher = StandardTestDispatcher(scope.testScheduler)
        addHandler { request ->
            assertEquals("Bearer ${tokens.read()!!.accessToken}", request.headers[HttpHeaders.Authorization])
            when (request.method) {
                HttpMethod.Get -> {
                    assertEquals("/api/v1/forest", request.url.encodedPath)
                    getCount++
                    respond(TreeAcknowledgementForest, headers = TreeAcknowledgementHeaders)
                }
                HttpMethod.Patch -> {
                    assertEquals("/api/v1/forest/trees/1", request.url.encodedPath)
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    patchBodies += body.getValue("acknowledgedAt").jsonPrimitive.content
                    patchHandler(this, patchBodies.size)
                }
                else -> error("서버 온보딩이 완료된 계정은 GET과 PATCH만 전송해야 합니다.")
            }
        }
    })) { configureRingoutHttpClient() }
    val repository = DefaultForestRepository(client, tokens, session, store) { flowOf(0) }
    private val coordinator = ForestSyncCoordinator(repository, session, object : NetworkMonitor {
        override val status = MutableStateFlow(NetworkStatus.Online)
    }) { scope.testScheduler.currentTime }
    private val syncJob = coordinator.start(scope.backgroundScope)
    private val viewModel = ForestViewModel(repository, scope.backgroundScope, session) { TreeFirstAt }
    val messages = mutableListOf<JsonObject>()
    private val connection = viewModel.connect(
        send = { messages += Json.parseToJsonElement(it).jsonObject },
        onReady = {},
        onBack = {},
        onError = { error("예상하지 않은 브릿지 오류") },
    )

    fun open() {
        viewModel.setActive(true)
        connection.receive(ForestProtocol.message("forest.ready", "ready"))
    }

    fun acknowledge() = connection.receive(ForestProtocol.message("forest.acknowledgeTree", "ack") {
        put("growthCycle", kotlinx.serialization.json.JsonPrimitive(1))
    })

    fun assertBridgeAcknowledged() {
        val result = messages.single { it["requestId"]?.jsonPrimitive?.content == "ack" }
        assertTrue(result.getValue("success").jsonPrimitive.boolean)
        val tree = result.getValue("payload").jsonObject.getValue("trees").jsonArray.first().jsonObject
        assertEquals(TreeFirstAt, tree.getValue("acknowledgedAtEpochMillis").jsonPrimitive.long)
    }

    suspend fun deliveryStatus() = store.read("1").treeAcknowledgementDeliveries.values.single().status

    fun closePage() {
        viewModel.setActive(false)
        connection.close()
    }

    suspend fun close() {
        closePage()
        syncJob.cancelAndJoin()
        client.close()
    }
}

private class TreeAcknowledgementPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        transform(data.value).also { data.value = it }
    }
}

private class TreeAcknowledgementTokens : SecureTokenStorage {
    private val claims = Base64.UrlSafe.encode("""{"userId":1,"sub":"1","tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=')
    private var value: AuthTokens? = AuthTokens("header.$claims.signature", "refresh")
    override suspend fun read() = value
    override suspend fun save(tokens: AuthTokens) { value = tokens }
    override suspend fun clear() { value = null }
}

private val TreeAcknowledgementHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private const val TreeFirstAt = 1_794_265_200_000L
private const val TreeServerAt = 1_794_265_260_000L
private const val TreeAcknowledgementForest = """{"isSuccess":true,"code":"FOREST200","message":"ok","result":{"growth":{"successfulDayCount":12,"filledSlots":2,"remainingDays":3,"isComplete":false},"onboardingCompletedAt":"2026-11-10T08:00:00+09:00","trees":[{"growthCycle":1,"kind":"pine","slotIndex":7,"plantedAt":"2026-11-05T07:48:10+09:00","acknowledgedAt":null},{"growthCycle":2,"kind":"oak","slotIndex":12,"plantedAt":"2026-11-10T07:48:10+09:00","acknowledgedAt":null}]}}"""
private const val TreeAcknowledgementSuccess = """{"isSuccess":true,"code":"FOREST200","message":"ok","result":{"growthCycle":1,"kind":"pine","slotIndex":7,"plantedAt":"2026-11-05T07:48:10+09:00","acknowledgedAt":"2026-11-10T08:01:00+09:00"}}"""
