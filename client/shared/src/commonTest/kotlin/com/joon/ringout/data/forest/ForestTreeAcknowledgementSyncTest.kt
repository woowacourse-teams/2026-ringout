package com.joon.ringout.data.forest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import com.joon.ringout.domain.forest.ForestTree
import com.joon.ringout.domain.forest.ForestTreeAcknowledgementSyncState
import com.joon.ringout.domain.forest.ForestTreeKind
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class ForestTreeAcknowledgementSyncTest {
    @Test
    fun `새 확인을 먼저 저장하고 인증된 PATCH에 최초 시각을 보내 서버 확인 시각을 채택한다`() = runTest {
        val f = AckFixture(this)
        f.prepare()
        f.repository.acknowledgeTree(1, AckFirstTime)
        f.repository.acknowledgeTree(1, AckFirstTime + 1_000)
        assertEquals(0, f.patchCount)
        assertEquals(AckFirstTime, f.repository.getForest().trees.single().acknowledgedAtEpochMillis)
        assertEquals(ForestTreeAcknowledgementSyncState.Pending, f.repository.observeTreeAcknowledgementSyncState().first())
        f.patchHandler = { request ->
            assertEquals("/api/v1/forest/trees/1", request.url.encodedPath)
            assertEquals("Bearer ${f.tokens.value!!.accessToken}", request.headers[HttpHeaders.Authorization])
            assertTrue(request.url.parameters.isEmpty())
            assertTrue(request.body.contentType.toString().startsWith("application/json"))
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("acknowledgedAt"), body.keys)
            assertEquals(Instant.fromEpochMilliseconds(AckFirstTime).toString(), body.getValue("acknowledgedAt").jsonPrimitive.content)
            respond(ackResponse(AckTree1, AckServerTime), headers = AckHeaders)
        }
        f.repository.syncForest(f.now)
        assertEquals(AckServerTime, f.repository.getForest().trees.single().acknowledgedAtEpochMillis)
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
        assertEquals(ForestTreeAcknowledgementSyncState.Synced, f.repository.observeTreeAcknowledgementSyncState().first())
        f.repository.syncForest(f.now)
        assertEquals(1, f.patchCount)
        f.close()
    }

    @Test
    fun `기존 로컬 확인은 중복 명령과 서버 미확인 응답에도 전송 대상으로 이관하지 않는다`() = runTest {
        val f = AckFixture(this)
        f.store.update("1") { it.copy(acknowledgedTrees = mapOf(AckTree1.localProgressKey() to AckFirstTime)) }
        f.prepare()
        f.repository.acknowledgeTree(1, AckFirstTime + 1_000)
        f.repository.refreshForest()
        f.repository.retryTreeAcknowledgementSync()
        f.repository.syncForest(f.now)
        assertEquals(0, f.patchCount)
        assertTrue(f.store.read("1").treeAcknowledgementDeliveries.isEmpty())
        f.serverTrees = listOf(AckTree1.copy(acknowledgedAtEpochMillis = AckServerTime))
        f.repository.refreshForest()
        assertEquals(AckServerTime, f.store.read("1").acknowledgedTrees[AckTree1.localProgressKey()])
        assertTrue(f.store.read("1").treeAcknowledgementDeliveries.isEmpty())
        f.close()
    }

    @Test
    fun `로컬 온보딩 완료만 있으면 서버 온보딩 확정까지 나무 전송을 기다린다`() = runTest {
        val f = AckFixture(this)
        f.serverOnboarding = null
        f.prepare()
        f.repository.completeOnboarding(AckOnboardingTime)
        f.repository.acknowledgeTree(1, AckFirstTime)
        val gate = CompletableDeferred<Unit>()
        f.putHandler = { gate.await(); respond(ackOnboardingResponse(), headers = AckHeaders) }
        val job = async { f.repository.syncForest(f.now) }
        runCurrent()
        assertEquals(1, f.putCount)
        assertEquals(0, f.patchCount)
        gate.complete(Unit)
        job.await()
        assertEquals(listOf(HttpMethod.Put, HttpMethod.Patch), f.requests.filter { it.method != HttpMethod.Get }.map { it.method })
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
        f.close()
    }

    @Test
    fun `한 나무의 전송 실패나 대기 기한이 다음 나무의 전송을 막지 않는다`() = runTest {
        val f = AckFixture(this)
        f.serverTrees = listOf(AckTree1, AckTree2)
        f.serverDays = 10
        f.prepare()
        f.repository.acknowledgeTree(1, AckFirstTime)
        f.repository.acknowledgeTree(2, AckFirstTime + 1)
        f.patchHandler = { request ->
            if (request.url.encodedPath.endsWith("/1")) respond(ackError("SERVER500"), HttpStatusCode.InternalServerError, AckHeaders)
            else respond(ackResponse(AckTree2), headers = AckHeaders)
        }
        assertEquals(5_000L, f.repository.syncForest(f.now))
        assertEquals(listOf(1, 2), f.patchCycles())
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Pending, f.delivery(1).status)
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery(2).status)
        f.repository.syncForest(f.now)
        assertEquals(2, f.patchCount)
        f.close()
    }

    @Test
    fun `재시작 뒤에도 최초 확인 시각과 느린 실패가 끝난 뒤의 기한을 보존한다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        f.patchHandler = { delay(6_000); respond(ackError("SERVER500"), HttpStatusCode.InternalServerError, AckHeaders) }
        val deadline = f.repository.syncForest(f.now)
        assertEquals(11_000L, deadline)
        val restored = f.newRepository()
        f.patchHandler = { respond(ackResponse(AckTree1), headers = AckHeaders) }
        assertEquals(deadline, restored.syncForest(f.now))
        assertEquals(1, f.patchCount)
        advanceTimeBy(5_000)
        restored.syncForest(f.now)
        assertEquals(2, f.patchCount)
        assertEquals(f.patchBodies().first(), f.patchBodies().last())
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
        f.close()
    }

    @Test
    fun `네트워크 오류와 일시적 HTTP 오류는 최초 확인을 유지하며 재시도한다`() = runTest {
        for (status in listOf(null, HttpStatusCode.RequestTimeout, HttpStatusCode.TooManyRequests, HttpStatusCode.ServiceUnavailable)) {
            val f = AckFixture(this)
            f.preparePending()
            f.patchHandler = {
                if (status == null) throw IOException("offline")
                respond(ackError("TEMPORARY"), status, AckHeaders)
            }
            val failedAt = testScheduler.currentTime
            assertEquals(failedAt + 5_000, f.repository.syncForest(f.now))
            assertEquals(AckFirstTime, f.delivery().acknowledgedAtEpochMillis)
            assertEquals(ForestTreeAcknowledgementDeliveryStatus.Pending, f.delivery().status)
            f.close()
        }
    }

    @Test
    fun `잘못된 요청과 다른 나무의 성공 응답은 자동 전송을 중단하고 수동 재시도로 복구한다`() = runTest {
        for ((status, body) in listOf(
            HttpStatusCode.BadRequest to ackError("FOREST400"),
            HttpStatusCode.Created to ackResponse(AckTree1),
            HttpStatusCode.OK to ackResponse(AckTree1, null),
            HttpStatusCode.OK to ackResponse(AckTree2),
            HttpStatusCode.OK to ackResponse(AckTree1.copy(slotIndex = 9)),
            HttpStatusCode.OK to """{"isSuccess":true,"code":"FOREST200","message":"ok","result":null}""",
        )) {
            val f = AckFixture(this)
            f.preparePending()
            f.patchHandler = { respond(body, status, AckHeaders) }
            f.repository.syncForest(f.now)
            f.repository.syncForest(f.now)
            assertEquals(1, f.patchCount)
            assertEquals(ForestTreeAcknowledgementSyncState.Blocked, f.repository.observeTreeAcknowledgementSyncState().first())
            assertEquals(AckFirstTime, f.repository.getForest().trees.single().acknowledgedAtEpochMillis)
            f.patchHandler = { respond(ackResponse(AckTree1), headers = AckHeaders) }
            f.repository.retryTreeAcknowledgementSync()
            f.repository.syncForest(f.now)
            assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
            assertEquals(f.patchBodies().first(), f.patchBodies().last())
            f.close()
        }
    }

    @Test
    fun `첫 충돌은 기존 온보딩 시각으로 재확정한 뒤 같은 나무만 다시 전송한다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        f.patchHandler = {
            if (f.patchCount == 1) respond(ackError("FOREST409"), HttpStatusCode.Conflict, AckHeaders)
            else respond(ackResponse(AckTree1), headers = AckHeaders)
        }
        val repairedAt = AckOnboardingTime - 1_000
        f.putHandler = { request ->
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(Instant.fromEpochMilliseconds(AckOnboardingTime).toString(), body.getValue("completedAt").jsonPrimitive.content)
            respond(ackOnboardingResponse(repairedAt), headers = AckHeaders)
        }
        f.repository.syncForest(f.now)
        assertEquals(listOf(HttpMethod.Patch, HttpMethod.Put, HttpMethod.Patch), f.requests.filter { it.method != HttpMethod.Get }.map { it.method })
        assertTrue(f.delivery().onboardingRepairCompleted)
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
        assertEquals(repairedAt, f.repository.getForest().onboardingCompletedAtEpochMillis)
        f.close()
    }

    @Test
    fun `온보딩 복구 성공 뒤 반복되는 충돌은 수동 재시도 전까지 차단한다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        f.patchHandler = { respond(ackError("FOREST409"), HttpStatusCode.Conflict, AckHeaders) }
        f.repository.syncForest(f.now)
        assertEquals(2, f.patchCount)
        assertEquals(1, f.putCount)
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Blocked, f.delivery().status)
        repeat(3) { f.repository.syncForest(f.now) }
        assertEquals(2, f.patchCount)
        assertEquals(1, f.putCount)
        f.repository.retryTreeAcknowledgementSync()
        f.repository.syncForest(f.now)
        assertEquals(4, f.patchCount)
        assertEquals(2, f.putCount)
        assertEquals(AckFirstTime, f.delivery().acknowledgedAtEpochMillis)
        f.close()
    }

    @Test
    fun `온보딩 복구의 일시적 실패는 재시작 후에도 복구 단계부터 이어간다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        f.patchHandler = {
            if (f.patchCount == 1) respond(ackError("FOREST409"), HttpStatusCode.Conflict, AckHeaders)
            else respond(ackResponse(AckTree1), headers = AckHeaders)
        }
        f.putHandler = { respond(ackError("SERVER500"), HttpStatusCode.InternalServerError, AckHeaders) }
        assertEquals(10_000L, f.repository.syncForest(f.now))
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.NeedsOnboarding, f.delivery().status)
        assertEquals(ForestOnboardingDeliveryStatus.Synced, f.store.read("1").onboardingSyncStatus)
        val restored = f.newRepository()
        f.putHandler = { respond(ackOnboardingResponse(), headers = AckHeaders) }
        advanceTimeBy(10_000)
        restored.syncForest(f.now)
        assertEquals(listOf(HttpMethod.Patch, HttpMethod.Put, HttpMethod.Put, HttpMethod.Patch), f.requests.filter { it.method != HttpMethod.Get }.map { it.method })
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
        f.close()
    }

    @Test
    fun `나무 없음보다 먼저 시작한 조회와 캐시 읽기는 보류를 풀지 않고 새 조회만 재개한다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        val gate = CompletableDeferred<Unit>()
        f.getHandler = { val body = f.forestBody(); gate.await(); respond(body, headers = AckHeaders) }
        val oldGet = async { f.repository.refreshForest() }
        runCurrent()
        f.patchHandler = { respond(ackError("FOREST404"), HttpStatusCode.NotFound, AckHeaders) }
        f.repository.syncForest(f.now)
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Blocked, f.delivery().status)
        gate.complete(Unit)
        oldGet.await()
        f.repository.getForest()
        f.repository.acknowledgeTree(1, AckFirstTime + 1_000)
        f.repository.syncForest(f.now)
        assertEquals(1, f.patchCount)
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Blocked, f.delivery().status)
        f.getHandler = null
        f.repository.refreshForest()
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Pending, f.delivery().status)
        assertEquals(5_000L, f.delivery().nextRetryAtEpochMillis)
        f.repository.syncForest(f.now)
        assertEquals(1, f.patchCount)
        f.patchHandler = { respond(ackResponse(AckTree1), headers = AckHeaders) }
        advanceTimeBy(5_000)
        f.repository.syncForest(f.now)
        assertEquals(2, f.patchCount)
        f.close()
    }

    @Test
    fun `없는 나무나 다른 나무의 조회는 확인을 재개하지 않고 수동 확인은 같은 나무에만 즉시 재시도한다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        f.patchHandler = { respond(ackError("FOREST404"), HttpStatusCode.NotFound, AckHeaders) }
        f.repository.syncForest(f.now)
        f.serverTrees = emptyList()
        f.repository.retryTreeAcknowledgementSync()
        f.repository.syncForest(f.now)
        assertEquals(1, f.patchCount)
        f.serverTrees = listOf(AckTree1.copy(slotIndex = 4))
        f.repository.refreshForest()
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Blocked, f.delivery().status)
        f.serverTrees = listOf(AckTree1)
        f.patchHandler = { respond(ackResponse(AckTree1), headers = AckHeaders) }
        f.repository.retryTreeAcknowledgementSync()
        f.repository.syncForest(f.now)
        assertEquals(2, f.patchCount)
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
        f.close()
    }

    @Test
    fun `수동 나무 확인 조회는 진행 중인 화면 조회를 취소하거나 최신 성장을 덮어쓰지 않는다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        f.patchHandler = { respond(ackError("FOREST404"), HttpStatusCode.NotFound, AckHeaders) }
        f.repository.syncForest(f.now)
        val gate = CompletableDeferred<Unit>()
        f.getHandler = {
            if (f.getCount == 2) { gate.await(); respond(f.forestBody(days = 12), headers = AckHeaders) }
            else respond(f.forestBody(days = 6), headers = AckHeaders)
        }
        val visibleGet = async { f.repository.refreshForest() }
        runCurrent()
        f.repository.retryTreeAcknowledgementSync()
        assertEquals(5, f.repository.getForest().growth.successfulDayCount)
        gate.complete(Unit)
        visibleGet.await()
        assertEquals(12, f.repository.getForest().growth.successfulDayCount)
        f.close()
    }

    @Test
    fun `확인 성공보다 늦은 미확인 조회는 서버에서 확정한 시각을 지우지 않는다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        val gate = CompletableDeferred<Unit>()
        f.getHandler = { val body = f.forestBody(); gate.await(); respond(body, headers = AckHeaders) }
        val oldGet = async { f.repository.refreshForest() }
        runCurrent()
        f.repository.syncForest(f.now)
        gate.complete(Unit)
        oldGet.await()
        assertEquals(AckServerTime, f.repository.getForest().trees.single().acknowledgedAtEpochMillis)
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
        f.close()
    }

    @Test
    fun `늦은 확인 응답은 최신 성장과 다른 나무를 유지한다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        val gate = CompletableDeferred<Unit>()
        f.patchHandler = { gate.await(); respond(ackResponse(AckTree1), headers = AckHeaders) }
        val patch = async { f.repository.syncForest(f.now) }
        runCurrent()
        f.serverDays = 13
        f.serverTrees = listOf(AckTree1, AckTree2)
        f.repository.refreshForest()
        gate.complete(Unit)
        patch.await()
        val state = f.repository.getForest()
        assertEquals(13, state.growth.successfulDayCount)
        assertEquals(2, state.trees.size)
        assertEquals(AckServerTime, state.trees.first().acknowledgedAtEpochMillis)
        assertNull(state.trees.last().acknowledgedAtEpochMillis)
        f.close()
    }

    @Test
    fun `조회로 확인된 뒤 도착한 전송 실패는 다시 미전송으로 바꾸지 않는다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        val gate = CompletableDeferred<Unit>()
        f.patchHandler = { gate.await(); respond(ackError("SERVER500"), HttpStatusCode.InternalServerError, AckHeaders) }
        val patch = async { f.repository.syncForest(f.now) }
        runCurrent()
        f.serverTrees = listOf(AckTree1.copy(acknowledgedAtEpochMillis = AckServerTime))
        f.repository.refreshForest()
        gate.complete(Unit)
        patch.await()
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
        assertNull(f.delivery().nextRetryAtEpochMillis)
        assertEquals(AckServerTime, f.delivery().acknowledgedAtEpochMillis)
        f.close()
    }

    @Test
    fun `서버 성공 뒤 로컬 저장 실패는 영구 차단하지 않고 다시 확정한다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        var fail = true
        val store = object : ForestLocalProgressStore by f.store {
            override suspend fun update(ownerAccountId: String, transform: (ForestLocalProgress) -> ForestLocalProgress): ForestLocalProgress =
                f.store.update(ownerAccountId) { before ->
                    transform(before).also { after ->
                        if (fail && after.treeAcknowledgementDeliveries.values.any { it.status == ForestTreeAcknowledgementDeliveryStatus.Synced }) {
                            fail = false
                            throw IOException("disk failure")
                        }
                    }
                }
        }
        val repository = DefaultForestRepository(f.client, f.tokens, f.session, store) { flowOf(0) }
        val job = ForestSyncCoordinator(repository, f.session, f.network, f.now).start(backgroundScope)
        runCurrent()
        assertEquals(1, f.patchCount)
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Pending, f.delivery().status)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, f.patchCount)
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
        job.cancelAndJoin()
        f.close()
    }

    @Test
    fun `동시에 요청한 동기화는 진행 중인 나무 확인 요청을 합친다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        val gate = CompletableDeferred<Unit>()
        f.patchHandler = { gate.await(); respond(ackResponse(AckTree1), headers = AckHeaders) }
        val first = async { f.repository.syncForest(f.now) }
        val second = async { f.repository.syncForest(f.now) }
        runCurrent()
        assertEquals(1, f.patchCount)
        assertEquals(ForestTreeAcknowledgementSyncState.Syncing, f.repository.observeTreeAcknowledgementSyncState().first())
        gate.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, f.patchCount)
        f.close()
    }

    @Test
    fun `인증 재발급 후에도 같은 최초 확인 본문으로 전송한다`() = runTest {
        val f = AckFixture(this, engineDispatcher = Dispatchers.Default)
        withContext(Dispatchers.Default) {
            f.preparePending()
            f.patchHandler = { request ->
                if (request.headers[HttpHeaders.Authorization] == "Bearer ${ackToken(1)}") {
                    respond(ackError("AUTH401"), HttpStatusCode.Unauthorized, AckHeaders)
                } else respond(ackResponse(AckTree1), headers = AckHeaders)
            }
            f.repository.syncForest(f.now)
        }
        assertEquals(2, f.patchCount)
        assertEquals(f.patchBodies().first(), f.patchBodies().last())
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
        f.close()
    }

    @Test
    fun `인증 만료는 확인 기록을 보존하고 새 로그인에서 전송을 재개한다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        f.patchHandler = { respond(ackError("COMMON401"), HttpStatusCode.Unauthorized, AckHeaders) }
        val job = f.coordinator.start(backgroundScope)
        runCurrent()
        assertEquals(AuthSessionState.ReauthenticationRequired, f.session.state.value)
        assertEquals(AckFirstTime, f.delivery().acknowledgedAtEpochMillis)
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Pending, f.delivery().status)
        f.tokens.value = AuthTokens(ackToken(1, "login"), "refresh")
        f.patchHandler = { respond(ackResponse(AckTree1), headers = AckHeaders) }
        f.session.startNewSession()
        runCurrent()
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery().status)
        job.cancelAndJoin()
        f.close()
    }

    @Test
    fun `이전 계정의 늦은 응답은 새 계정의 전송을 막거나 확인 상태를 바꾸지 않는다`() = runTest {
        val f = AckFixture(this)
        f.preparePending()
        f.store.update("2") { it.copy(onboardingCompletedAtEpochMillis = AckOnboardingTime, onboardingSyncStatus = ForestOnboardingDeliveryStatus.Synced) }
        f.store.acknowledgeTree("2", AckTree1, AckFirstTime + 1_000)
        val gate = CompletableDeferred<Unit>()
        f.patchHandler = { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer ${ackToken(1)}") withContext(NonCancellable) { gate.await() }
            respond(ackResponse(AckTree1), headers = AckHeaders)
        }
        val job = f.coordinator.start(backgroundScope)
        runCurrent()
        f.tokens.value = AuthTokens(ackToken(2), "refresh-2")
        f.session.startNewSession()
        runCurrent()
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Synced, f.delivery(owner = "2").status)
        gate.complete(Unit)
        runCurrent()
        assertEquals(ForestTreeAcknowledgementDeliveryStatus.Pending, f.delivery(owner = "1").status)
        assertEquals(AckFirstTime, f.delivery(owner = "1").acknowledgedAtEpochMillis)
        job.cancelAndJoin()
        f.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class AckFixture(
    scope: TestScope,
    engineDispatcher: CoroutineDispatcher = StandardTestDispatcher(scope.testScheduler),
) {
    val now = { scope.testScheduler.currentTime }
    val preferences = AckPreferences()
    val store = DataStoreForestLocalProgressStore(preferences)
    val session = AuthSession().apply { startNewSession() }
    val tokens = AckTokens()
    val network = AckNetwork()
    val requests = mutableListOf<HttpRequestData>()
    val patchCount get() = requests.count { it.method == HttpMethod.Patch }
    val putCount get() = requests.count { it.method == HttpMethod.Put }
    val getCount get() = requests.count { it.method == HttpMethod.Get }
    var serverOnboarding: Long? = AckOnboardingTime
    var serverDays = 5
    var serverTrees = listOf(AckTree1)
    var getHandler: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData)? = null
    var patchHandler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
        val cycle = request.url.encodedPath.substringAfterLast('/').toInt()
        respond(ackResponse(serverTrees.single { it.growthCycle == cycle }), headers = AckHeaders)
    }
    var putHandler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond(ackOnboardingResponse(), headers = AckHeaders)
    }
    val client = HttpClient(MockEngine(MockEngineConfig().apply {
        dispatcher = engineDispatcher
        addHandler { request ->
            requests += request
            when {
                request.url.encodedPath == "/api/v1/auth/reissue" -> respond(
                    """{"isSuccess":true,"code":"AUTH200","message":"ok","result":{"accessToken":"${ackToken(1, "renewed")}","refreshToken":"renewed"}}""",
                    headers = AckHeaders,
                )
                request.method == HttpMethod.Get -> getHandler?.invoke(this, request) ?: respond(forestBody(), headers = AckHeaders)
                request.method == HttpMethod.Patch -> patchHandler(this, request)
                request.method == HttpMethod.Put -> putHandler(this, request)
                else -> error("Unexpected request")
            }
        }
    })) { configureRingoutHttpClient() }
    fun newRepository() = DefaultForestRepository(client, tokens, session, store) { flowOf(0) }
    val repository = newRepository()
    val coordinator = ForestSyncCoordinator(repository, session, network, now)
    suspend fun prepare() { repository.refreshForest() }
    suspend fun preparePending() { prepare(); repository.acknowledgeTree(1, AckFirstTime) }
    suspend fun delivery(cycle: Int = 1, owner: String = "1") = store.read(owner).treeAcknowledgementDeliveries.values.single { it.growthCycle == cycle }
    fun patchCycles() = requests.filter { it.method == HttpMethod.Patch }.map { it.url.encodedPath.substringAfterLast('/').toInt() }
    fun patchBodies() = requests.filter { it.method == HttpMethod.Patch }.map { (it.body as TextContent).text }
    fun forestBody(days: Int = serverDays) = """{"isSuccess":true,"code":"FOREST200","message":"ok","result":{"growth":{"successfulDayCount":$days,"filledSlots":${days % 5},"remainingDays":${5 - days % 5},"isComplete":false},"onboardingCompletedAt":${ackTime(serverOnboarding)},"trees":[${serverTrees.joinToString(",") { ackTreeJson(it, it.acknowledgedAtEpochMillis) }}]}}"""
    fun close() = client.close()
}

private class AckTokens : SecureTokenStorage {
    var value: AuthTokens? = AuthTokens(ackToken(1), "refresh")
    override suspend fun read() = value
    override suspend fun save(tokens: AuthTokens) { value = tokens }
    override suspend fun clear() { value = null }
}
private class AckNetwork : NetworkMonitor {
    override val status = MutableStateFlow(NetworkStatus.Online)
}
private class AckPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        transform(data.value).also { data.value = it }
    }
}
private val AckHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private const val AckOnboardingTime = 1_794_000_000_000L
private const val AckFirstTime = 1_794_265_200_000L
private const val AckServerTime = 1_794_092_400_000L
private val AckTree1 = ForestTree(1, ForestTreeKind.Pine, 7, 1_793_840_890_000L)
private val AckTree2 = ForestTree(2, ForestTreeKind.Oak, 12, 1_793_840_990_000L)
private fun ackTime(time: Long?) = time?.let { "\"${Instant.fromEpochMilliseconds(it)}\"" } ?: "null"
private fun ackTreeJson(tree: ForestTree, time: Long?) = """{"growthCycle":${tree.growthCycle},"kind":"${tree.kind.persistedValue}","slotIndex":${tree.slotIndex},"plantedAt":${ackTime(tree.plantedAtEpochMillis)},"acknowledgedAt":${ackTime(time)}}"""
private fun ackResponse(tree: ForestTree, time: Long? = AckServerTime) = """{"isSuccess":true,"code":"FOREST200","message":"ok","result":${ackTreeJson(tree, time)}}"""
private fun ackOnboardingResponse(time: Long = AckOnboardingTime) = """{"isSuccess":true,"code":"FOREST200","message":"ok","result":{"onboardingCompletedAt":${ackTime(time)}}}"""
private fun ackError(code: String) = """{"isSuccess":false,"code":"$code","message":"error","result":null}"""
private fun ackToken(owner: Int, suffix: String = "original") =
    "header.${Base64.UrlSafe.encode("""{"userId":$owner,"sub":"$owner","tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=')}.$suffix"
