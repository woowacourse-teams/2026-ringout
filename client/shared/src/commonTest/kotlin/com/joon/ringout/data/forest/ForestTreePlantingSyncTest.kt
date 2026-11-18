package com.joon.ringout.data.forest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceAccount
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import com.joon.ringout.domain.forest.ForestPlantingSyncState
import com.joon.ringout.domain.forest.ForestTree
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ForestTreePlantingSyncTest {
    @Test
    fun `재설치 후 기기 도착 한 건과 서버 성공 오일로 온보딩 없이 첫 나무를 저장한다`() = runTest {
        val f = PlantFixture(this)
        f.repository.recordConfirmedArrival(f.account(), "arrival", PlantArrival)
        assertEquals(0, f.getCount)
        assertTrue(f.repository.resolvePendingArrivals(f.account(), f.now).maySendArrival)
        val original = f.delivery()
        assertEquals(1, original.growthCycle)
        assertEquals(PlantArrival, original.plantedAtEpochMillis)
        assertTrue(original.kind in setOf("pine", "oak", "big_tree"))
        assertNull(f.repository.getForest().onboardingCompletedAtEpochMillis)
        assertTrue(f.repository.getForest().trees.isEmpty())
        f.putHandler = { request ->
            assertEquals("/api/v1/forest/trees/1", request.url.encodedPath)
            assertEquals("Bearer ${plantToken(1)}", request.headers[HttpHeaders.Authorization])
            assertTrue(request.url.parameters.isEmpty())
            assertTrue(request.body.contentType.toString().startsWith("application/json"))
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("kind", "plantedAt"), body.keys)
            assertEquals(original.kind, body.getValue("kind").jsonPrimitive.content)
            assertEquals(Instant.fromEpochMilliseconds(PlantArrival).toString(), body.getValue("plantedAt").jsonPrimitive.content)
            respond(plantResponse(PlantCanonical), HttpStatusCode.Created, PlantHeaders)
        }
        f.repository.syncForest(f.now)
        assertEquals(ForestTreePlantingDeliveryStatus.Synced, f.delivery().status)
        assertEquals(PlantCanonical, f.repository.getForest().trees.single())
        assertEquals(5, f.repository.getForest().growth.successfulDayCount)
        assertEquals(ForestPlantingSyncState.Synced, f.repository.observePlantingSyncState().first())
        f.close()
    }

    @Test
    fun `일반 조회와 기존 로컬 나무 정보만으로 과거 획득을 소급 저장하지 않는다`() = runTest {
        val f = PlantFixture(this)
        f.serverDays = 20
        f.store.update("1") { it.copy(acknowledgedTrees = mapOf(PlantCanonical.localProgressKey() to PlantArrival)) }
        f.repository.refreshForest()
        f.repository.syncForest(f.now)
        assertEquals(0, f.putCount)
        assertTrue(f.store.read("1").arrivalPlantingCheckpoints.isEmpty())
        assertTrue(f.store.read("1").treePlantingDeliveries.isEmpty())
        f.close()
    }

    @Test
    fun `중복 도착과 같은 한국 날짜의 재도착에도 최초 추첨과 도착 시각을 유지한다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        val first = f.delivery()
        f.repository.recordConfirmedArrival(f.account(), "arrival", PlantArrival)
        f.repository.resolvePendingArrivals(f.account(), f.now)
        assertEquals(1, f.getCount)
        f.repository.recordConfirmedArrival(f.account(), "another-arrival", PlantArrival + 60_000)
        f.repository.resolvePendingArrivals(f.account(), f.now)
        assertEquals(first, f.delivery())
        assertEquals(1, f.store.read("1").treePlantingDeliveries.size)
        assertFailsWith<IllegalArgumentException> { f.repository.recordConfirmedArrival(f.account(), "arrival", PlantArrival + 1) }
        f.close()
    }

    @Test
    fun `이미 서버에 존재하는 획득 회차는 다시 추첨하거나 저장하지 않는다`() = runTest {
        val f = PlantFixture(this)
        f.serverTrees = listOf(PlantCanonical)
        f.prepare()
        f.repository.syncForest(f.now)
        assertEquals(0, f.putCount)
        assertTrue(f.store.read("1").treePlantingDeliveries.isEmpty())
        assertEquals(listOf(PlantCanonical), f.repository.getForest().trees)
        f.close()
    }

    @Test
    fun `서버의 오일 단위만 획득하고 팔십일 이후에는 나무를 추가하지 않는다`() = runTest {
        for (days in listOf(0, 4, 6, 23, 80, 81, 95)) {
            val f = PlantFixture(this)
            f.serverDays = days
            f.prepare()
            val deliveries = f.store.read("1").treePlantingDeliveries
            if (days == 80) assertEquals(setOf(16), deliveries.keys) else assertTrue(deliveries.isEmpty(), "$days")
            f.close()
        }
    }

    @Test
    fun `획득 판단은 도착 전에 받은 캐시 대신 새 조회의 성공일을 사용한다`() = runTest {
        val f = PlantFixture(this)
        f.repository.refreshForest()
        f.serverDays = 6
        f.prepare()
        assertEquals(2, f.getCount)
        assertTrue(f.store.read("1").treePlantingDeliveries.isEmpty())
        assertEquals(6, f.store.read("1").arrivalPlantingCheckpoints.getValue("arrival").successfulDayCount)
        f.close()
    }

    @Test
    fun `조회 실패 뒤 다음 도착을 보류하고 느린 실패 종료 시점부터 재시도한다`() = runTest {
        val f = PlantFixture(this)
        f.repository.recordConfirmedArrival(f.account(), "arrival", PlantArrival)
        f.getHandler = { delay(10_000); respond(plantError("FOREST500"), HttpStatusCode.InternalServerError, PlantHeaders) }
        val first = f.repository.resolvePendingArrivals(f.account(), f.now)
        assertFalse(first.maySendArrival)
        assertEquals(15_000L, first.nextRetryAtEpochMillis)
        val restored = f.newRepository()
        assertEquals(first, restored.resolvePendingArrivals(f.account(), f.now))
        assertEquals(1, f.getCount)
        f.getHandler = null
        advanceTimeBy(5_000)
        assertTrue(restored.resolvePendingArrivals(f.account(), f.now).maySendArrival)
        assertEquals(PlantArrival, f.delivery().plantedAtEpochMillis)
        assertEquals(ForestArrivalPlantingStatus.Resolved, f.store.read("1").arrivalPlantingCheckpoints.getValue("arrival").status)
        f.close()
    }

    @Test
    fun `잘못된 조회 응답은 자동 판단을 중단하고 수동 재시도에서만 다시 조회한다`() = runTest {
        val f = PlantFixture(this)
        f.getHandler = { respond("{}", headers = PlantHeaders) }
        f.repository.recordConfirmedArrival(f.account(), "arrival", PlantArrival)
        assertFalse(f.repository.resolvePendingArrivals(f.account(), f.now).maySendArrival)
        assertEquals(ForestPlantingSyncState.Blocked, f.repository.observePlantingSyncState().first())
        f.repository.resolvePendingArrivals(f.account(), f.now)
        assertEquals(1, f.getCount)
        f.getHandler = null
        f.repository.retryPlantingSync()
        assertTrue(f.repository.resolvePendingArrivals(f.account(), f.now).maySendArrival)
        assertEquals(2, f.getCount)
        f.close()
    }

    @Test
    fun `동시에 판단을 요청해도 같은 도착을 한 번만 조회한다`() = runTest {
        val f = PlantFixture(this)
        f.repository.recordConfirmedArrival(f.account(), "arrival", PlantArrival)
        val gate = CompletableDeferred<Unit>()
        f.getHandler = { gate.await(); respond(f.forestBody(), headers = PlantHeaders) }
        val first = async { f.repository.resolvePendingArrivals(f.account(), f.now) }
        val second = async { f.repository.resolvePendingArrivals(f.account(), f.now) }
        runCurrent()
        assertEquals(1, f.getCount)
        gate.complete(Unit)
        assertTrue(first.await().maySendArrival)
        assertTrue(second.await().maySendArrival)
        assertEquals(1, f.getCount)
        f.close()
    }

    @Test
    fun `늦은 획득 판단은 자기 회차를 저장하되 최신 화면 조회의 성장을 덮어쓰지 않는다`() = runTest {
        val f = PlantFixture(this)
        val gate = CompletableDeferred<Unit>()
        f.getHandler = {
            val body = f.forestBody()
            if (f.getCount == 1) gate.await()
            respond(body, headers = PlantHeaders)
        }
        f.repository.recordConfirmedArrival(f.account(), "arrival", PlantArrival)
        val evaluation = async { f.repository.resolvePendingArrivals(f.account(), f.now) }
        runCurrent()
        f.serverDays = 6
        f.repository.refreshForest()
        gate.complete(Unit)
        assertTrue(evaluation.await().maySendArrival)
        assertEquals(6, f.repository.getForest().growth.successfulDayCount)
        assertEquals(1, f.delivery().growthCycle)
        f.close()
    }

    @Test
    fun `늦은 화면 조회는 먼저 완료된 획득 판단의 성장을 되돌리지 않고 정상 완료한다`() = runTest {
        val f = PlantFixture(this)
        val gate = CompletableDeferred<Unit>()
        f.serverDays = 4
        f.getHandler = {
            val body = f.forestBody()
            if (f.getCount == 1) gate.await()
            respond(body, headers = PlantHeaders)
        }
        val ui = async { f.repository.refreshForest() }
        runCurrent()
        f.serverDays = 5
        f.prepare()
        gate.complete(Unit)
        assertEquals(5, ui.await().growth.successfulDayCount)
        assertEquals(5, f.repository.getForest().growth.successfulDayCount)
        f.close()
    }

    @Test
    fun `느린 식재 응답을 기다리지 않고 다음 도착의 획득 판단을 끝낸다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        val gate = CompletableDeferred<Unit>()
        f.putHandler = { gate.await(); respond(plantResponse(PlantCanonical), headers = PlantHeaders) }
        val upload = async { f.repository.syncForest(f.now) }
        runCurrent()
        f.serverDays = 6
        f.repository.recordConfirmedArrival(f.account(), "next", PlantArrival + 86_400_000)
        assertTrue(f.repository.resolvePendingArrivals(f.account(), f.now).maySendArrival)
        assertFalse(upload.isCompleted)
        gate.complete(Unit)
        upload.await()
        assertEquals(6, f.repository.getForest().growth.successfulDayCount)
        assertEquals(listOf(PlantCanonical), f.repository.getForest().trees)
        f.close()
    }

    @Test
    fun `식재 실패 후 재시작해도 최초 종류와 시각으로 같은 본문을 재전송한다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        f.putHandler = { delay(10_000); respond(plantError("FOREST500"), HttpStatusCode.InternalServerError, PlantHeaders) }
        assertEquals(15_000L, f.repository.syncForest(f.now))
        val first = f.delivery()
        val restored = f.newRepository()
        assertEquals(15_000L, restored.syncForest(f.now))
        assertEquals(1, f.putCount)
        f.putHandler = { respond(plantResponse(PlantCanonical), headers = PlantHeaders) }
        advanceTimeBy(5_000)
        restored.syncForest(f.now)
        assertEquals(2, f.putCount)
        assertEquals(f.putBodies().first(), f.putBodies().last())
        assertEquals(first.kind, f.delivery().kind)
        assertEquals(first.plantedAtEpochMillis, f.delivery().plantedAtEpochMillis)
        assertEquals(ForestTreePlantingDeliveryStatus.Synced, f.delivery().status)
        f.close()
    }

    @Test
    fun `조회 성공 뒤 저장 실패는 판단 완료와 PUT 대기 기록을 일부만 남기지 않는다`() = runTest {
        val f = PlantFixture(this)
        f.repository.recordConfirmedArrival(f.account(), "arrival", PlantArrival)
        f.getHandler = {
            f.preferences.failNextUpdate = true
            respond(f.forestBody(), headers = PlantHeaders)
        }
        assertFailsWith<IOException> { f.repository.resolvePendingArrivals(f.account(), f.now) }
        val progress = f.store.read("1")
        assertEquals(ForestArrivalPlantingStatus.Pending, progress.arrivalPlantingCheckpoints.getValue("arrival").status)
        assertTrue(progress.treePlantingDeliveries.isEmpty())
        f.getHandler = null
        assertTrue(f.repository.resolvePendingArrivals(f.account(), f.now).maySendArrival)
        assertEquals(PlantArrival, f.delivery().plantedAtEpochMillis)
        f.close()
    }

    @Test
    fun `식재 성공 뒤 로컬 저장 실패는 차단하지 않고 같은 본문으로 다시 확정한다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        f.putHandler = {
            f.preferences.failNextUpdate = true
            respond(plantResponse(PlantCanonical), headers = PlantHeaders)
        }
        assertFailsWith<IOException> { f.repository.syncForest(f.now) }
        assertEquals(ForestTreePlantingDeliveryStatus.Pending, f.delivery().status)
        f.putHandler = { respond(plantResponse(PlantCanonical), headers = PlantHeaders) }
        f.repository.syncForest(f.now)
        assertEquals(ForestTreePlantingDeliveryStatus.Synced, f.delivery().status)
        assertEquals(f.putBodies().first(), f.putBodies().last())
        f.close()
    }

    @Test
    fun `잘못된 식재 요청과 다른 회차의 성공 응답은 차단하고 최초 본문으로 수동 재시도한다`() = runTest {
        for (invalidResponse in listOf(false, true)) {
            val f = PlantFixture(this)
            f.prepare()
            f.putHandler = {
                if (invalidResponse) respond(plantResponse(PlantCanonical.copy(growthCycle = 2)), headers = PlantHeaders)
                else respond(plantError("FOREST400"), HttpStatusCode.BadRequest, PlantHeaders)
            }
            f.repository.syncForest(f.now)
            assertEquals(ForestTreePlantingDeliveryStatus.Blocked, f.delivery().status)
            f.repository.syncForest(f.now)
            assertEquals(1, f.putCount)
            f.repository.retryPlantingSync()
            f.putHandler = { respond(plantResponse(PlantCanonical), headers = PlantHeaders) }
            f.repository.syncForest(f.now)
            assertEquals(f.putBodies().first(), f.putBodies().last())
            assertEquals(ForestTreePlantingDeliveryStatus.Synced, f.delivery().status)
            f.close()
        }
    }

    @Test
    fun `식재 충돌의 수동 확인은 새 조회만 하고 서버에 없으면 PUT을 반복하지 않는다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        f.putHandler = { respond(plantError("FOREST409"), HttpStatusCode.Conflict, PlantHeaders) }
        f.repository.syncForest(f.now)
        f.repository.retryPlantingSync()
        f.repository.syncForest(f.now)
        assertEquals(1, f.putCount)
        assertEquals(2, f.getCount)
        assertEquals(ForestTreePlantingDeliveryStatus.Blocked, f.delivery().status)
        assertNull(f.store.read("1").onboardingCompletedAtEpochMillis)
        f.serverTrees = listOf(PlantCanonical.copy(acknowledgedAtEpochMillis = PlantAcknowledged))
        f.repository.retryPlantingSync()
        assertEquals(ForestTreePlantingDeliveryStatus.Synced, f.delivery().status)
        assertEquals(PlantAcknowledged, f.repository.getForest().trees.single().acknowledgedAtEpochMillis)
        assertEquals(1, f.putCount)
        f.close()
    }

    @Test
    fun `이백 응답의 다른 종류와 시각 및 이미 확인된 상태를 모두 채택한다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        val pending = f.delivery()
        val kind = ForestTreeKind.entries.first { it.persistedValue != pending.kind }
        val canonical = PlantCanonical.copy(kind = kind, acknowledgedAtEpochMillis = PlantAcknowledged)
        f.putHandler = { respond(plantResponse(canonical), headers = PlantHeaders) }
        f.repository.syncForest(f.now)
        assertEquals(canonical, f.repository.getForest().trees.single())
        assertEquals(canonical, f.delivery().confirmedTree!!.toDomain())
        f.close()
    }

    @Test
    fun `식재 성공보다 늦은 빈 조회는 확정 나무를 지우지 않는다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        val gate = CompletableDeferred<Unit>()
        f.getHandler = { val body = f.forestBody(); gate.await(); respond(body, headers = PlantHeaders) }
        val get = async { f.repository.refreshForest() }
        runCurrent()
        f.repository.syncForest(f.now)
        gate.complete(Unit)
        assertEquals(listOf(PlantCanonical), get.await().trees)
        assertEquals(listOf(PlantCanonical), f.repository.getForest().trees)
        f.close()
    }

    @Test
    fun `조회로 식재가 확정된 뒤 늦은 PUT 실패가 미전송으로 되돌리지 않는다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        val gate = CompletableDeferred<Unit>()
        f.putHandler = { gate.await(); respond(plantError("FOREST500"), HttpStatusCode.InternalServerError, PlantHeaders) }
        val upload = async { f.repository.syncForest(f.now) }
        runCurrent()
        f.serverTrees = listOf(PlantCanonical)
        f.repository.refreshForest()
        gate.complete(Unit)
        upload.await()
        assertEquals(ForestTreePlantingDeliveryStatus.Synced, f.delivery().status)
        assertEquals(listOf(PlantCanonical), f.repository.getForest().trees)
        f.close()
    }

    @Test
    fun `늦은 미확인 PUT 응답은 이미 조회한 서버 확인 시각을 지우지 않는다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        val gate = CompletableDeferred<Unit>()
        f.putHandler = { gate.await(); respond(plantResponse(PlantCanonical), headers = PlantHeaders) }
        val upload = async { f.repository.syncForest(f.now) }
        runCurrent()
        f.serverDays = 6
        f.serverTrees = listOf(PlantCanonical.copy(acknowledgedAtEpochMillis = PlantAcknowledged))
        f.repository.refreshForest()
        gate.complete(Unit)
        upload.await()
        assertEquals(6, f.repository.getForest().growth.successfulDayCount)
        assertEquals(PlantAcknowledged, f.repository.getForest().trees.single().acknowledgedAtEpochMillis)
        f.close()
    }

    @Test
    fun `식재된 나무의 확인 PATCH 성공은 늦은 미확인 조회에도 유지된다`() = runTest {
        val f = PlantFixture(this)
        f.serverOnboarding = PlantArrival - 1_000
        f.prepare()
        f.repository.syncForest(f.now)
        f.repository.acknowledgeTree(1, PlantAcknowledged)
        f.repository.syncForest(f.now)
        f.serverTrees = listOf(PlantCanonical)
        f.repository.refreshForest()
        assertEquals(PlantAcknowledged, f.repository.getForest().trees.single().acknowledgedAtEpochMillis)
        assertEquals(PlantAcknowledged, f.delivery().confirmedTree?.acknowledgedAtEpochMillis)
        f.close()
    }

    @Test
    fun `한 회차가 실패해도 다른 회차의 식재를 전송한다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        f.serverDays = 10
        f.repository.recordConfirmedArrival(f.account(), "ten", PlantArrival + 5 * 86_400_000L)
        f.repository.resolvePendingArrivals(f.account(), f.now)
        f.putHandler = { request ->
            if (request.url.encodedPath.endsWith("/1")) respond(plantError("FOREST500"), HttpStatusCode.InternalServerError, PlantHeaders)
            else respond(plantResponse(PlantCanonical.copy(growthCycle = 2, slotIndex = 12)), headers = PlantHeaders)
        }
        assertEquals(5_000L, f.repository.syncForest(f.now))
        assertEquals(2, f.putCount)
        assertEquals(ForestTreePlantingDeliveryStatus.Pending, f.delivery(1).status)
        assertEquals(ForestTreePlantingDeliveryStatus.Synced, f.delivery(2).status)
        f.close()
    }

    @Test
    fun `인증 재발급 후에도 최초 식재 본문을 그대로 보낸다`() = runTest {
        val f = PlantFixture(this, Dispatchers.Default)
        withContext(Dispatchers.Default) {
            f.prepare()
            f.putHandler = { request ->
                if (request.headers[HttpHeaders.Authorization] == "Bearer ${plantToken(1)}") {
                    respond(plantError("AUTH401"), HttpStatusCode.Unauthorized, PlantHeaders)
                } else respond(plantResponse(PlantCanonical), headers = PlantHeaders)
            }
            f.repository.syncForest(f.now)
        }
        assertEquals(2, f.putCount)
        assertEquals(f.putBodies().first(), f.putBodies().last())
        assertEquals(ForestTreePlantingDeliveryStatus.Synced, f.delivery().status)
        f.close()
    }

    @Test
    fun `인증 만료 뒤 새 로그인에서 저장한 식재를 재개한다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        f.putHandler = { respond(plantError("COMMON401"), HttpStatusCode.Unauthorized, PlantHeaders) }
        val job = f.coordinator.start(backgroundScope)
        runCurrent()
        assertEquals(AuthSessionState.ReauthenticationRequired, f.session.state.value)
        assertEquals(ForestTreePlantingDeliveryStatus.Pending, f.delivery().status)
        f.tokens.value = AuthTokens(plantToken(1, "login"), "refresh")
        f.putHandler = { respond(plantResponse(PlantCanonical), headers = PlantHeaders) }
        f.session.startNewSession()
        runCurrent()
        assertEquals(ForestTreePlantingDeliveryStatus.Synced, f.delivery().status)
        job.cancelAndJoin()
        f.close()
    }

    @Test
    fun `이전 계정의 늦은 식재 응답은 새 계정의 저장을 막거나 덮어쓰지 않는다`() = runTest {
        val f = PlantFixture(this)
        f.prepare()
        f.store.update("2") { it.copy(treePlantingDeliveries = mapOf(1 to ForestTreePlantingDelivery(1, "pine", PlantArrival))) }
        val gate = CompletableDeferred<Unit>()
        f.putHandler = { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer ${plantToken(1)}") withContext(NonCancellable) { gate.await() }
            respond(plantResponse(PlantCanonical), headers = PlantHeaders)
        }
        val job = f.coordinator.start(backgroundScope)
        runCurrent()
        f.tokens.value = AuthTokens(plantToken(2), "refresh-2")
        f.session.startNewSession()
        runCurrent()
        assertEquals(ForestTreePlantingDeliveryStatus.Synced, f.delivery(owner = "2").status)
        gate.complete(Unit)
        runCurrent()
        assertEquals(ForestTreePlantingDeliveryStatus.Pending, f.delivery(owner = "1").status)
        job.cancelAndJoin()
        f.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class PlantFixture(
    scope: TestScope,
    engineDispatcher: CoroutineDispatcher = StandardTestDispatcher(scope.testScheduler),
) {
    val now = { scope.testScheduler.currentTime }
    val preferences = PlantPreferences()
    val store = DataStoreForestLocalProgressStore(preferences)
    val session = AuthSession().apply { startNewSession() }
    val tokens = PlantTokens()
    val requests = mutableListOf<HttpRequestData>()
    val putCount get() = requests.count { it.method == HttpMethod.Put }
    val getCount get() = requests.count { it.method == HttpMethod.Get }
    var serverDays = 5
    var serverTrees = emptyList<ForestTree>()
    var serverOnboarding: Long? = null
    var getHandler: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData)? = null
    var putHandler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond(plantResponse(PlantCanonical), headers = PlantHeaders)
    }
    val client = HttpClient(MockEngine(MockEngineConfig().apply {
        dispatcher = engineDispatcher
        addHandler { request ->
            requests += request
            when {
                request.url.encodedPath == "/api/v1/auth/reissue" -> respond(
                    """{"isSuccess":true,"code":"AUTH200","message":"ok","result":{"accessToken":"${plantToken(1, "renewed")}","refreshToken":"renewed"}}""",
                    headers = PlantHeaders,
                )
                request.method == HttpMethod.Get -> getHandler?.invoke(this, request) ?: respond(forestBody(), headers = PlantHeaders)
                request.method == HttpMethod.Put -> putHandler(this, request)
                request.method == HttpMethod.Patch -> respond(plantResponse(PlantCanonical.copy(acknowledgedAtEpochMillis = PlantAcknowledged)), headers = PlantHeaders)
                else -> error("Unexpected request")
            }
        }
    })) { configureRingoutHttpClient() }
    fun newRepository() = DefaultForestRepository(client, tokens, session, store) { flowOf(0) }
    val repository = newRepository()
    val coordinator = ForestSyncCoordinator(repository, session, object : NetworkMonitor {
        override val status = MutableStateFlow(NetworkStatus.Online)
    }, now)
    fun account(owner: String = "1") = AlarmOccurrenceAccount(owner, checkNotNull(session.identity.value))
    suspend fun prepare() {
        repository.recordConfirmedArrival(account(), "arrival", PlantArrival)
        assertTrue(repository.resolvePendingArrivals(account(), now).maySendArrival)
    }
    suspend fun delivery(cycle: Int = 1, owner: String = "1") = store.read(owner).treePlantingDeliveries.getValue(cycle)
    fun putBodies() = requests.filter { it.method == HttpMethod.Put }.map { (it.body as TextContent).text }
    fun forestBody(): String {
        val filled = if (serverDays >= 80) 0 else serverDays % 5
        val remaining = if (serverDays >= 80) 0 else 5 - filled
        return """{"isSuccess":true,"code":"FOREST200","message":"ok","result":{"growth":{"successfulDayCount":$serverDays,"filledSlots":$filled,"remainingDays":$remaining,"isComplete":${serverDays >= 80}},"onboardingCompletedAt":${plantTime(serverOnboarding)},"trees":[${serverTrees.joinToString(",", transform = ::plantTreeJson)}]}}"""
    }
    fun close() = client.close()
}

private class PlantPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    var failNextUpdate = false
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        if (failNextUpdate) {
            failNextUpdate = false
            throw IOException("storage unavailable")
        }
        transform(data.value).also { data.value = it }
    }
}
private class PlantTokens : SecureTokenStorage {
    var value: AuthTokens? = AuthTokens(plantToken(1), "refresh")
    override suspend fun read() = value
    override suspend fun save(tokens: AuthTokens) { value = tokens }
    override suspend fun clear() { value = null }
}
private val PlantHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private const val PlantArrival = 1_794_265_200_000L
private const val PlantAcknowledged = PlantArrival + 100_000L
private val PlantCanonical = ForestTree(1, ForestTreeKind.Oak, 7, PlantArrival - 60_000)
private fun plantTime(time: Long?) = time?.let { "\"${Instant.fromEpochMilliseconds(it)}\"" } ?: "null"
private fun plantTreeJson(tree: ForestTree) = """{"growthCycle":${tree.growthCycle},"kind":"${tree.kind.persistedValue}","slotIndex":${tree.slotIndex},"plantedAt":${plantTime(tree.plantedAtEpochMillis)},"acknowledgedAt":${plantTime(tree.acknowledgedAtEpochMillis)}}"""
private fun plantResponse(tree: ForestTree) = """{"isSuccess":true,"code":"FOREST200","message":"ok","result":${plantTreeJson(tree)}}"""
private fun plantError(code: String) = """{"isSuccess":false,"code":"$code","message":"error","result":null}"""
private fun plantToken(owner: Int, suffix: String = "original") =
    "header.${Base64.UrlSafe.encode("""{"userId":$owner,"sub":"$owner","tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=')}.$suffix"
