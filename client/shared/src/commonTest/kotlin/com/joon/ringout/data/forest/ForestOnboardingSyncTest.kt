package com.joon.ringout.data.forest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import com.joon.ringout.domain.forest.ForestOnboardingSyncState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ForestOnboardingSyncTest {
    @Test
    fun `완료 명령은 서버를 기다리지 않고 첫 시각을 저장해 즉시 숲에 진입할 상태를 반환한다`() = runTest {
        val fixture = OnboardingFixture(this)
        fixture.repository.refreshForest()
        fixture.repository.completeOnboarding(FirstTime)
        fixture.repository.completeOnboarding(FirstTime + 1_000)
        assertEquals(0, fixture.puts)
        assertEquals(FirstTime, fixture.repository.getForest().onboardingCompletedAtEpochMillis)
        assertEquals(FirstTime, fixture.store.read("1").onboardingCompletedAtEpochMillis)
        assertEquals(ForestOnboardingSyncState.Pending, fixture.repository.observeOnboardingSyncState().first())
        fixture.close()
    }

    @Test
    fun `인증된 PUT은 같은 최초 시각을 보내고 생성과 기존 완료 응답의 서버 시각을 채택한다`() = runTest {
        for (status in listOf(HttpStatusCode.Created, HttpStatusCode.OK)) {
            val fixture = OnboardingFixture(this)
            fixture.handler = { request ->
                assertEquals(HttpMethod.Put, request.method)
                assertEquals("/api/v1/forest/onboarding", request.url.encodedPath)
                assertTrue(request.url.parameters.isEmpty())
                assertEquals("Bearer ${fixture.tokens.value!!.accessToken}", request.headers[HttpHeaders.Authorization])
                assertTrue(request.body.contentType.toString().startsWith("application/json"))
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals(setOf("completedAt"), body.keys)
                assertEquals(Instant.fromEpochMilliseconds(FirstTime).toString(), body.getValue("completedAt").jsonPrimitive.content)
                respond(onboardingBody(ServerTime), status, Headers)
            }
            fixture.repository.refreshForest()
            fixture.repository.completeOnboarding(FirstTime)
            fixture.repository.syncOnboarding(fixture.now)
            assertEquals(ServerTime, fixture.repository.getForest().onboardingCompletedAtEpochMillis)
            assertEquals(ServerTime, fixture.store.read("1").onboardingCompletedAtEpochMillis)
            assertEquals(ForestOnboardingSyncState.Synced, fixture.repository.observeOnboardingSyncState().first())
            fixture.repository.syncOnboarding(fixture.now)
            assertEquals(1, fixture.puts)
            fixture.close()
        }
    }

    @Test
    fun `서버에서 이미 완료된 숲을 조회하면 로컬 기록을 확정하고 PUT을 생략한다`() = runTest {
        val fixture = OnboardingFixture(this)
        fixture.store.completeOnboarding("1", FirstTime)
        fixture.getBody = forestBody(5, ServerTime)
        fixture.repository.refreshForest()
        fixture.repository.syncOnboarding(fixture.now)
        assertEquals(0, fixture.puts)
        assertEquals(ForestOnboardingDeliveryStatus.Synced, fixture.store.read("1").onboardingSyncStatus)
        assertEquals(ServerTime, fixture.repository.getForest().onboardingCompletedAtEpochMillis)
        fixture.close()
    }

    @Test
    fun `기존 JSON 완료 기록은 숲 화면 없이 앱 동기화에서 자동 이관한다`() = runTest {
        val preferences = OnboardingPreferences()
        preferences.updateData { it.toMutablePreferences().apply {
            this[stringPreferencesKey("forest_pending_progress_1")] = """{"onboardingCompletedAtEpochMillis":$FirstTime,"acknowledgedTrees":{"tree":123}}"""
        } }
        val fixture = OnboardingFixture(this, preferences)
        val job = fixture.coordinator.start(backgroundScope)
        runCurrent()
        assertEquals(1, fixture.puts)
        assertEquals(0, fixture.gets)
        assertEquals(mapOf("tree" to 123L), fixture.store.read("1").acknowledgedTrees)
        assertEquals(ForestOnboardingDeliveryStatus.Synced, fixture.store.read("1").onboardingSyncStatus)
        job.cancelAndJoin()
        fixture.close()
    }

    @Test
    fun `새 완료 기록은 네트워크 복구 시 화면 수명과 무관하게 한 번 전송한다`() = runTest {
        val fixture = OnboardingFixture(this)
        fixture.network.status.value = NetworkStatus.Offline
        val job = fixture.coordinator.start(backgroundScope)
        fixture.repository.refreshForest()
        fixture.repository.completeOnboarding(FirstTime)
        runCurrent()
        assertEquals(0, fixture.puts)
        fixture.network.status.value = NetworkStatus.Online
        fixture.coordinator.requestSync()
        fixture.coordinator.requestSync()
        runCurrent()
        assertEquals(1, fixture.puts)
        assertEquals(ForestOnboardingDeliveryStatus.Synced, fixture.store.read("1").onboardingSyncStatus)
        job.cancelAndJoin()
        fixture.close()
    }

    @Test
    fun `느린 서버 실패가 끝난 시각부터 재시도 간격을 계산한다`() = runTest {
        val fixture = OnboardingFixture(this)
        fixture.store.completeOnboarding("1", FirstTime)
        fixture.handler = { delay(6_000); respond(apiError("FOREST500"), HttpStatusCode.InternalServerError, Headers) }
        val job = fixture.coordinator.start(backgroundScope)
        runCurrent()
        advanceTimeBy(6_000); runCurrent()
        assertEquals(11_000L, fixture.store.read("1").onboardingNextRetryAtEpochMillis)
        assertEquals(1, fixture.puts)
        advanceTimeBy(4_999); runCurrent()
        assertEquals(1, fixture.puts)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, fixture.puts)
        job.cancelAndJoin()
        fixture.close()
    }

    @Test
    fun `재실행해도 대기시간과 최초 시각을 보존하고 만료 후 같은 본문을 재전송한다`() = runTest {
        val fixture = OnboardingFixture(this)
        val bodies = mutableListOf<String>()
        fixture.handler = { request ->
            bodies += (request.body as TextContent).text
            if (bodies.size == 1) respond(apiError("FOREST500"), HttpStatusCode.InternalServerError, Headers)
            else respond(onboardingBody(ServerTime), HttpStatusCode.Created, Headers)
        }
        fixture.store.completeOnboarding("1", FirstTime)
        val first = fixture.coordinator.start(backgroundScope)
        runCurrent()
        assertEquals(5_000L, fixture.store.read("1").onboardingNextRetryAtEpochMillis)
        first.cancelAndJoin()
        val restoredRepository = fixture.newRepository()
        val restored = ForestOnboardingSyncCoordinator(restoredRepository, fixture.session, fixture.network, fixture.now)
        val second = restored.start(backgroundScope)
        runCurrent()
        assertEquals(1, fixture.puts)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, fixture.puts)
        assertEquals(bodies.first(), bodies.last())
        assertEquals(ForestOnboardingDeliveryStatus.Synced, fixture.store.read("1").onboardingSyncStatus)
        second.cancelAndJoin()
        fixture.close()
    }

    @Test
    fun `일시적인 네트워크와 서버 오류는 완료를 유지하고 다음 전송 시각을 저장한다`() = runTest {
        for (status in listOf(null, HttpStatusCode.RequestTimeout, HttpStatusCode.TooManyRequests, HttpStatusCode.ServiceUnavailable)) {
            val fixture = OnboardingFixture(this)
            fixture.store.completeOnboarding("1", FirstTime)
            fixture.handler = {
                if (status == null) throw IOException("offline")
                respond(apiError("TEMPORARY"), status, Headers)
            }
            val retryAt = fixture.repository.syncOnboarding(fixture.now)
            assertEquals(fixture.now() + 5_000L, retryAt)
            val pending = fixture.store.read("1")
            assertEquals(FirstTime, pending.onboardingCompletedAtEpochMillis)
            assertEquals(ForestOnboardingDeliveryStatus.Pending, pending.onboardingSyncStatus)
            assertEquals(1, pending.onboardingAttemptCount)
            fixture.repository.syncOnboarding(fixture.now)
            assertEquals(1, fixture.puts)
            fixture.close()
        }
    }

    @Test
    fun `서버 성공 뒤 저장 실패는 영구 차단하지 않고 앱 동기화에서 다시 확정한다`() = runTest {
        val fixture = OnboardingFixture(this)
        fixture.store.completeOnboarding("1", FirstTime)
        var failConfirmation = true
        val failingStore = object : ForestLocalProgressStore by fixture.store {
            override suspend fun update(ownerAccountId: String, transform: (ForestLocalProgress) -> ForestLocalProgress): ForestLocalProgress =
                fixture.store.update(ownerAccountId) { current ->
                    transform(current).also { updated ->
                        if (failConfirmation && updated.onboardingSyncStatus == ForestOnboardingDeliveryStatus.Synced) {
                            failConfirmation = false
                            throw IOException("temporary disk failure")
                        }
                    }
                }
        }
        val repository = DefaultForestRepository(fixture.client, fixture.tokens, fixture.session, failingStore) { flowOf(0) }
        val coordinator = ForestOnboardingSyncCoordinator(repository, fixture.session, fixture.network, fixture.now)
        val job = coordinator.start(backgroundScope)
        runCurrent()
        assertEquals(1, fixture.puts)
        assertEquals(ForestOnboardingDeliveryStatus.Pending, fixture.store.read("1").onboardingSyncStatus)
        assertEquals(FirstTime, fixture.store.read("1").onboardingCompletedAtEpochMillis)
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, fixture.puts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, fixture.puts)
        assertEquals(ForestOnboardingDeliveryStatus.Synced, fixture.store.read("1").onboardingSyncStatus)
        assertEquals(ServerTime, fixture.store.read("1").onboardingCompletedAtEpochMillis)
        job.cancelAndJoin()
        fixture.close()
    }

    @Test
    fun `동시에 들어온 전송은 진행 중 요청을 합치고 이미 확정한 기록을 다시 보내지 않는다`() = runTest {
        val fixture = OnboardingFixture(this)
        fixture.store.completeOnboarding("1", FirstTime)
        val gate = CompletableDeferred<Unit>()
        fixture.handler = { gate.await(); respond(onboardingBody(ServerTime), headers = Headers) }
        val first = async { fixture.repository.syncOnboarding(fixture.now) }
        val second = async { fixture.repository.syncOnboarding(fixture.now) }
        runCurrent()
        assertEquals(1, fixture.puts)
        assertEquals(ForestOnboardingSyncState.Syncing, fixture.repository.observeOnboardingSyncState().first())
        gate.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, fixture.puts)
        fixture.close()
    }

    @Test
    fun `잘못된 요청과 성공 응답은 완료를 유지한 채 차단하고 수동 재시도로 복구한다`() = runTest {
        for ((status, body) in listOf(
            HttpStatusCode.BadRequest to apiError("FOREST400"),
            HttpStatusCode.OK to """{"isSuccess":true,"code":"FOREST200","message":"OK","result":null}""",
            HttpStatusCode.OK to """{"isSuccess":true,"code":"FOREST200","message":"OK","result":{"onboardingCompletedAt":"invalid"}}""",
        )) {
            val fixture = OnboardingFixture(this)
            fixture.handler = { respond(body, status, Headers) }
            fixture.repository.refreshForest()
            fixture.repository.completeOnboarding(FirstTime)
            fixture.repository.syncOnboarding(fixture.now)
            assertEquals(FirstTime, fixture.repository.getForest().onboardingCompletedAtEpochMillis)
            assertEquals(ForestOnboardingSyncState.Blocked, fixture.repository.observeOnboardingSyncState().first())
            fixture.repository.refreshForest()
            fixture.repository.syncOnboarding(fixture.now)
            assertEquals(1, fixture.puts)
            assertEquals(ForestOnboardingSyncState.Blocked, fixture.repository.observeOnboardingSyncState().first())
            fixture.handler = { respond(onboardingBody(ServerTime), headers = Headers) }
            fixture.repository.retryOnboardingSync()
            assertEquals(1, fixture.puts)
            fixture.repository.syncOnboarding(fixture.now)
            assertEquals(2, fixture.puts)
            assertEquals(ServerTime, fixture.repository.getForest().onboardingCompletedAtEpochMillis)
            fixture.close()
        }
    }

    @Test
    fun `PUT 성공보다 늦게 도착한 미완료 GET은 완료 시각을 지우지 않는다`() = runTest {
        val fixture = OnboardingFixture(this)
        fixture.repository.refreshForest()
        fixture.repository.completeOnboarding(FirstTime)
        val gate = CompletableDeferred<Unit>()
        fixture.getHandler = { gate.await(); respond(forestBody(9), headers = Headers) }
        val read = async { fixture.repository.refreshForest() }
        runCurrent()
        fixture.repository.syncOnboarding(fixture.now)
        gate.complete(Unit)
        assertEquals(ServerTime, read.await().onboardingCompletedAtEpochMillis)
        assertEquals(9, fixture.repository.getForest().growth.successfulDayCount)
        fixture.close()
    }

    @Test
    fun `늦은 PUT 성공은 그동안 갱신된 성장 상태를 되돌리지 않는다`() = runTest {
        val fixture = OnboardingFixture(this)
        fixture.repository.refreshForest()
        fixture.repository.completeOnboarding(FirstTime)
        val gate = CompletableDeferred<Unit>()
        fixture.handler = { gate.await(); respond(onboardingBody(ServerTime), headers = Headers) }
        val write = async { fixture.repository.syncOnboarding(fixture.now) }
        runCurrent()
        fixture.getBody = forestBody(9)
        fixture.repository.refreshForest()
        gate.complete(Unit)
        write.await()
        assertEquals(9, fixture.repository.getForest().growth.successfulDayCount)
        assertEquals(ServerTime, fixture.repository.getForest().onboardingCompletedAtEpochMillis)
        fixture.close()
    }

    @Test
    fun `GET에서 확정한 뒤 늦은 PUT 실패가 와도 미전송으로 되돌리지 않는다`() = runTest {
        val fixture = OnboardingFixture(this)
        fixture.store.completeOnboarding("1", FirstTime)
        val gate = CompletableDeferred<Unit>()
        fixture.handler = { gate.await(); respond(apiError("FOREST500"), HttpStatusCode.InternalServerError, Headers) }
        val write = async { fixture.repository.syncOnboarding(fixture.now) }
        runCurrent()
        fixture.getBody = forestBody(9, ServerTime)
        fixture.repository.refreshForest()
        gate.complete(Unit)
        assertNull(write.await())
        assertEquals(ForestOnboardingDeliveryStatus.Synced, fixture.store.read("1").onboardingSyncStatus)
        assertNull(fixture.store.read("1").onboardingNextRetryAtEpochMillis)
        fixture.close()
    }

    @Test
    fun `인증 재발급 후 같은 완료 본문을 한 번 더 전송한다`() = runTest {
        // The shared authentication executor refreshes on Dispatchers.Default, outside virtual time.
        val fixture = OnboardingFixture(this, engineDispatcher = Dispatchers.Default)
        val bodies = mutableListOf<String>()
        val original = fixture.tokens.value!!.accessToken
        fixture.handler = { request ->
            if (request.url.encodedPath == "/api/v1/auth/reissue") {
                respond("""{"isSuccess":true,"code":"AUTH200","message":"OK","result":{"accessToken":"${token(1, "renewed")}","refreshToken":"renewed"}}""", headers = Headers)
            } else {
                bodies += (request.body as TextContent).text
                if (request.headers[HttpHeaders.Authorization] == "Bearer $original") respond(apiError("AUTH401"), HttpStatusCode.Unauthorized, Headers)
                else respond(onboardingBody(ServerTime), headers = Headers)
            }
        }
        fixture.store.completeOnboarding("1", FirstTime)
        withContext(Dispatchers.Default) { fixture.repository.syncOnboarding(fixture.now) }
        assertEquals(2, bodies.size)
        assertEquals(bodies.first(), bodies.last())
        assertEquals(ForestOnboardingDeliveryStatus.Synced, fixture.store.read("1").onboardingSyncStatus)
        fixture.close()
    }

    @Test
    fun `인증 만료 뒤에는 미전송 기록을 보존하고 새 로그인에서 재개한다`() = runTest {
        val fixture = OnboardingFixture(this)
        fixture.handler = { respond(apiError("COMMON401"), HttpStatusCode.Unauthorized, Headers) }
        fixture.store.completeOnboarding("1", FirstTime)
        val job = fixture.coordinator.start(backgroundScope)
        runCurrent()
        assertEquals(AuthSessionState.ReauthenticationRequired, fixture.session.state.value)
        assertEquals(FirstTime, fixture.store.read("1").onboardingCompletedAtEpochMillis)
        assertEquals(ForestOnboardingDeliveryStatus.Pending, fixture.store.read("1").onboardingSyncStatus)
        fixture.handler = { respond(onboardingBody(ServerTime), headers = Headers) }
        fixture.tokens.value = AuthTokens(token(1, "login"), "refresh")
        fixture.session.startNewSession()
        runCurrent()
        assertEquals(ForestOnboardingDeliveryStatus.Synced, fixture.store.read("1").onboardingSyncStatus)
        job.cancelAndJoin()
        fixture.close()
    }

    @Test
    fun `계정이 바뀌면 취소가 늦은 이전 응답을 기다리지 않고 새 계정만 확정한다`() = runTest {
        val fixture = OnboardingFixture(this)
        fixture.store.completeOnboarding("1", FirstTime)
        fixture.store.completeOnboarding("2", FirstTime + 1_000)
        val gate = CompletableDeferred<Unit>()
        fixture.handler = { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer ${token(1)}") {
                withContext(NonCancellable) { gate.await() }
            }
            respond(onboardingBody(ServerTime), headers = Headers)
        }
        val job = fixture.coordinator.start(backgroundScope)
        runCurrent()
        fixture.tokens.value = AuthTokens(token(2), "refresh-2")
        fixture.session.startNewSession()
        runCurrent()
        assertEquals(ForestOnboardingDeliveryStatus.Synced, fixture.store.read("2").onboardingSyncStatus)
        assertEquals(ForestOnboardingDeliveryStatus.Pending, fixture.store.read("1").onboardingSyncStatus)
        gate.complete(Unit)
        runCurrent()
        assertEquals(FirstTime, fixture.store.read("1").onboardingCompletedAtEpochMillis)
        job.cancelAndJoin()
        fixture.close()
    }

    @Test
    fun `재시도 간격은 오초부터 증가하며 십오분을 넘지 않는다`() {
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L), (1..4).map(::onboardingRetryDelayMillis))
        assertEquals(900_000L, onboardingRetryDelayMillis(20))
        assertEquals(900_000L, onboardingRetryDelayMillis(Int.MAX_VALUE))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class OnboardingFixture(
    scope: TestScope,
    preferences: OnboardingPreferences = OnboardingPreferences(),
    engineDispatcher: CoroutineDispatcher = StandardTestDispatcher(scope.testScheduler),
) {
    val now = { scope.testScheduler.currentTime }
    val session = AuthSession().apply { startNewSession() }
    val tokens = OnboardingTokens()
    val network = OnboardingNetwork()
    val store = DataStoreForestLocalProgressStore(preferences)
    var puts = 0
    var gets = 0
    var getBody = forestBody(0)
    var getHandler: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData)? = null
    var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond(onboardingBody(ServerTime), HttpStatusCode.Created, Headers)
    }
    val client = HttpClient(MockEngine(MockEngineConfig().apply {
        dispatcher = engineDispatcher
        addHandler { request ->
            if (request.method == HttpMethod.Get) {
                gets++
                getHandler?.invoke(this, request) ?: respond(getBody, headers = Headers)
            } else {
                if (request.method == HttpMethod.Put) puts++
                handler(this, request)
            }
        }
    })) { configureRingoutHttpClient() }
    fun newRepository() = DefaultForestRepository(client, tokens, session, store) { flowOf(0) }
    val repository = newRepository()
    val coordinator = ForestOnboardingSyncCoordinator(repository, session, network, now)
    fun close() = client.close()
}

private class OnboardingTokens : SecureTokenStorage {
    var value: AuthTokens? = AuthTokens(token(1), "refresh")
    override suspend fun read() = value
    override suspend fun save(tokens: AuthTokens) { value = tokens }
    override suspend fun clear() { value = null }
}
private class OnboardingNetwork : NetworkMonitor {
    override val status = MutableStateFlow(NetworkStatus.Online)
}
private class OnboardingPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        transform(data.value).also { data.value = it }
    }
}
private val Headers = headersOf(HttpHeaders.ContentType, "application/json")
private const val FirstTime = 1_794_265_200_000L
private const val ServerTime = 1_794_092_400_000L
private fun onboardingBody(time: Long) = """{"isSuccess":true,"code":"FOREST200","message":"OK","result":{"onboardingCompletedAt":"${Instant.fromEpochMilliseconds(time)}"}}"""
private fun apiError(code: String) = """{"isSuccess":false,"code":"$code","message":"error","result":null}"""
private fun token(owner: Int, suffix: String = "original") =
    "header.${Base64.UrlSafe.encode("""{"userId":$owner,"sub":"$owner","tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=')}.$suffix"
private fun forestBody(days: Int, completedAt: Long? = null): String =
    """{"isSuccess":true,"code":"FOREST200","message":"OK","result":{"growth":{"successfulDayCount":$days,"filledSlots":${days % 5},"remainingDays":${5 - days % 5},"isComplete":false},"onboardingCompletedAt":${completedAt?.let { "\"${Instant.fromEpochMilliseconds(it)}\"" } ?: "null"},"trees":[]}}"""
