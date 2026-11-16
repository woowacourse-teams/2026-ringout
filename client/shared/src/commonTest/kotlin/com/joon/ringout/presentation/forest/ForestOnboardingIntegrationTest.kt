package com.joon.ringout.presentation.forest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.joon.ringout.data.forest.DataStoreForestLocalProgressStore
import com.joon.ringout.data.forest.DefaultForestRepository
import com.joon.ringout.data.forest.ForestOnboardingDeliveryStatus
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.forest.ForestOnboardingSyncState
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ForestOnboardingIntegrationTest {
    @Test
    fun `웹은 로컬 완료로 즉시 숲에 진입하고 화면 종료 뒤에도 진행 중인 전송의 서버 시각이 저장된다`() = runTest {
        val putGate = CompletableDeferred<Unit>()
        val fixture = OnboardingIntegrationFixture(this) {
            putGate.await()
            respond(onboardingSuccess(), HttpStatusCode.Created, IntegrationHeaders)
        }
        try {
            fixture.open()
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.hasSnapshot)
            assertEquals(1, fixture.getCount)
            val appSync = backgroundScope.async {
                fixture.repository.observeOnboardingSyncState().first { it == ForestOnboardingSyncState.Pending }
                fixture.repository.syncOnboarding { testScheduler.currentTime }
            }

            fixture.complete()
            runCurrent()

            assertEquals(1, fixture.putBodies.size)
            assertFalse(appSync.isCompleted)
            fixture.assertBridgeCompleted()
            assertEquals(IntegrationCompletedAt, fixture.repository.getForest().onboardingCompletedAtEpochMillis)
            assertEquals(ForestOnboardingDeliveryStatus.Pending, fixture.store.read("1").onboardingSyncStatus)
            fixture.closePage()
            val deliveredCount = fixture.messages.size

            putGate.complete(Unit)
            appSync.await()
            runCurrent()

            val restored = DataStoreForestLocalProgressStore(fixture.preferences).read("1")
            assertEquals(IntegrationServerAt, restored.onboardingCompletedAtEpochMillis)
            assertEquals(ForestOnboardingDeliveryStatus.Synced, restored.onboardingSyncStatus)
            assertEquals(deliveredCount, fixture.messages.size)
            assertEquals(1, fixture.getCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `일시적인 전송 실패에도 웹 완료와 로컬 시각을 유지하고 앱 재시도는 같은 최초 시각을 전송한다`() = runTest {
        val fixture = OnboardingIntegrationFixture(this) { attempt ->
            if (attempt == 1) {
                respond(
                    """{"isSuccess":false,"code":"SERVER500","message":"retry","result":null}""",
                    HttpStatusCode.InternalServerError,
                    IntegrationHeaders,
                )
            } else respond(onboardingSuccess(), HttpStatusCode.OK, IntegrationHeaders)
        }
        try {
            fixture.open()
            runCurrent()
            fixture.complete()
            runCurrent()
            val firstSync = backgroundScope.async { fixture.repository.syncOnboarding { testScheduler.currentTime } }
            val retryAt = firstSync.await()
            runCurrent()

            fixture.assertBridgeCompleted()
            assertEquals(IntegrationCompletedAt, fixture.repository.getForest().onboardingCompletedAtEpochMillis)
            val pending = fixture.store.read("1")
            assertEquals(ForestOnboardingDeliveryStatus.Pending, pending.onboardingSyncStatus)
            assertEquals(IntegrationCompletedAt, pending.onboardingCompletedAtEpochMillis)
            assertEquals(5_000L, retryAt)
            assertEquals(retryAt, pending.onboardingNextRetryAtEpochMillis)
            assertNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
            fixture.closePage()

            advanceTimeBy(5_000)
            backgroundScope.async { fixture.repository.syncOnboarding { testScheduler.currentTime } }.await()
            runCurrent()

            val expectedTime = Instant.fromEpochMilliseconds(IntegrationCompletedAt).toString()
            assertEquals(listOf(expectedTime, expectedTime), fixture.putBodies)
            val synced = DataStoreForestLocalProgressStore(fixture.preferences).read("1")
            assertEquals(ForestOnboardingDeliveryStatus.Synced, synced.onboardingSyncStatus)
            assertEquals(IntegrationServerAt, synced.onboardingCompletedAtEpochMillis)
            assertEquals(1, fixture.getCount)
        } finally {
            fixture.close()
        }
    }
}

private class OnboardingIntegrationFixture(
    scope: TestScope,
    putHandler: suspend MockRequestHandleScope.(Int) -> HttpResponseData,
) {
    val preferences = OnboardingIntegrationPreferences()
    val store = DataStoreForestLocalProgressStore(preferences)
    private val session = AuthSession().apply { startNewSession() }
    private val tokens = OnboardingIntegrationTokens()
    val putBodies = mutableListOf<String>()
    var getCount = 0
    private val client = HttpClient(MockEngine(MockEngineConfig().apply {
        dispatcher = StandardTestDispatcher(scope.testScheduler)
        addHandler { request ->
            assertEquals("Bearer ${tokens.read()!!.accessToken}", request.headers[HttpHeaders.Authorization])
            if (request.method == HttpMethod.Get) {
                assertEquals("/api/v1/forest", request.url.encodedPath)
                getCount++
                respond(IntegrationEmptyForest, headers = IntegrationHeaders)
            } else {
                assertEquals(HttpMethod.Put, request.method)
                assertEquals("/api/v1/forest/onboarding", request.url.encodedPath)
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                putBodies += body.getValue("completedAt").jsonPrimitive.content
                putHandler(this, putBodies.size)
            }
        }
    })) { configureRingoutHttpClient() }
    private val arrivals = MutableStateFlow(0)
    val repository = DefaultForestRepository(client, tokens, session, store) { arrivals }
    val viewModel = ForestViewModel(repository, scope.backgroundScope, session) { IntegrationCompletedAt }
    val messages = mutableListOf<JsonObject>()
    private val connection = viewModel.connect(
        send = { messages += Json.parseToJsonElement(it).jsonObject },
        onReady = {},
        onBack = {},
        onError = { error("예상하지 않은 브리지 오류") },
    )

    fun open() {
        viewModel.setActive(true)
        connection.receive(ForestProtocol.message("forest.ready", "ready"))
    }

    fun complete() = connection.receive(ForestProtocol.message("forest.completeOnboarding", "complete"))

    fun assertBridgeCompleted() {
        val result = messages.single { it["requestId"]?.jsonPrimitive?.content == "complete" }
        assertTrue(result.getValue("success").jsonPrimitive.boolean)
        val payload = result.getValue("payload").jsonObject
        assertEquals(IntegrationCompletedAt, payload.getValue("onboardingCompletedAtEpochMillis").jsonPrimitive.long)
    }

    fun closePage() {
        viewModel.setActive(false)
        connection.close()
    }

    fun close() {
        closePage()
        client.close()
    }
}

private class OnboardingIntegrationPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        transform(data.value).also { data.value = it }
    }
}

private class OnboardingIntegrationTokens : SecureTokenStorage {
    private val claims = Base64.UrlSafe.encode("""{"userId":1,"sub":"1","tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=')
    private var value: AuthTokens? = AuthTokens("header.$claims.signature", "refresh")
    override suspend fun read() = value
    override suspend fun save(tokens: AuthTokens) { value = tokens }
    override suspend fun clear() { value = null }
}

private val IntegrationHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private const val IntegrationCompletedAt = 1_794_265_200_000L
private const val IntegrationServerAt = 1_794_092_400_000L
private const val IntegrationEmptyForest = """{"isSuccess":true,"code":"FOREST200","message":"ok","result":{"growth":{"successfulDayCount":0,"filledSlots":0,"remainingDays":5,"isComplete":false},"onboardingCompletedAt":null,"trees":[]}}"""
private fun onboardingSuccess() = """{"isSuccess":true,"code":"FOREST200","message":"ok","result":{"onboardingCompletedAt":"${Instant.fromEpochMilliseconds(IntegrationServerAt)}"}}"""
