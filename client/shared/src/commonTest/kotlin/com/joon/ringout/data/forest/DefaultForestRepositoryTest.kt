package com.joon.ringout.data.forest

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.forest.ForestState
import com.joon.ringout.domain.forest.ForestTree
import com.joon.ringout.domain.forest.ForestTreeKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultForestRepositoryTest {
    @Test
    fun `인증된 조회만 요청하고 현재 상태 읽기는 추가 요청 없이 반환한다`() = runTest {
        var requests = 0
        val tokens = ForestTestTokens()
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/forest", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())
            assertEquals("Bearer ${tokens.value!!.accessToken}", request.headers[HttpHeaders.Authorization])
            assertTrue(request.body is OutgoingContent.NoContent)
            respond(forestBody(23, listOf(treeJson(1, "pine", 7), treeJson(2, "oak", 12))), headers = forestHeaders)
        }) { configureRingoutHttpClient() }
        val repo = repository(client, tokens)

        val state = repo.refreshForest()

        assertEquals(state, repo.getForest())
        assertEquals(1, requests)
        assertEquals(23, state.growth.successfulDayCount)
        assertEquals(3, state.growth.filledSlots)
        assertEquals(2, state.growth.remainingDays)
        assertEquals(listOf(7, 12), state.trees.map { it.slotIndex })
        assertEquals(listOf(ForestTreeKind.Pine, ForestTreeKind.Oak), state.trees.map { it.kind })
        assertEquals(Instant.parse("2026-11-05T07:48:10+09:00").toEpochMilliseconds(), state.trees.first().plantedAtEpochMillis)
        assertNull(state.onboardingCompletedAtEpochMillis)
        assertNull(state.trees.last().acknowledgedAtEpochMillis)
        client.close()
    }

    @Test
    fun `빈 숲을 성공 상태로 반환하고 첫 씨앗 진행을 보존한다`() = runTest {
        val client = forestClient { forestBody(0) }
        val state = repository(client).refreshForest()
        assertTrue(state.trees.isEmpty())
        assertNull(state.onboardingCompletedAtEpochMillis)
        assertEquals(0, state.growth.filledSlots)
        assertEquals(5, state.growth.remainingDays)
        assertFalse(state.growth.isComplete)
        client.close()
    }

    @Test
    fun `성장이 완료되어도 전체 성공일을 보존하고 늦게 저장되는 나무를 생성하지 않는다`() = runTest {
        for (days in listOf(80, 95)) {
            val client = forestClient { forestBody(days, listOf(treeJson(1, "big_tree", 15))) }
            val state = repository(client).refreshForest()
            assertEquals(days, state.growth.successfulDayCount)
            assertTrue(state.growth.isComplete)
            assertEquals(0, state.growth.filledSlots)
            assertEquals(0, state.growth.remainingDays)
            assertEquals(1, state.trees.size)
            assertEquals(ForestTreeKind.BigTree, state.trees.single().kind)
            client.close()
        }
    }

    @Test
    fun `서버의 온보딩과 나무 확인 시각을 오프셋에 맞춰 변환한다`() = runTest {
        val time = "2026-11-10T08:00:00+09:00"
        val client = forestClient { forestBody(5, listOf(treeJson(1, acknowledged = "\"$time\"")), "\"$time\"") }
        val state = repository(client).refreshForest()
        assertEquals(Instant.parse(time).toEpochMilliseconds(), state.onboardingCompletedAtEpochMillis)
        assertEquals(state.onboardingCompletedAtEpochMillis, state.trees.single().acknowledgedAtEpochMillis)
        client.close()
    }

    @Test
    fun `조회 실패를 빈 숲으로 만들지 않고 마지막 성공 상태를 유지한다`() = runTest {
        var fail = false
        val client = HttpClient(MockEngine {
            if (fail) respond("""{"isSuccess":false,"code":"FOREST500","message":"조회 실패","result":null}""",
                HttpStatusCode.InternalServerError, forestHeaders)
            else respond(forestBody(3), headers = forestHeaders)
        }) { configureRingoutHttpClient() }
        val repo = repository(client)
        val previous = repo.refreshForest()
        fail = true
        val error = assertFailsWith<ApiException> { repo.refreshForest() }
        assertEquals(500, error.statusCode)
        assertEquals("FOREST500", error.code)
        assertEquals(previous, repo.getForest())
        client.close()
    }

    @Test
    fun `최초 응답이 비어 있거나 실패 봉투이면 성공 상태를 만들지 않는다`() = runTest {
        for (body in listOf(
            """{"isSuccess":true,"code":"FOREST200","message":"OK","result":null}""",
            """{"isSuccess":false,"code":"FOREST500","message":"실패","result":null}""",
        )) {
            val client = forestClient { body }
            val repo = repository(client)
            assertFailsWith<IllegalStateException> { repo.refreshForest() }
            assertFailsWith<IllegalStateException> { repo.getForest() }
            client.close()
        }
    }

    @Test
    fun `성장 계산과 응답이 모순이면 해당 응답을 저장하지 않는다`() = runTest {
        val client = forestClient { forestBody(23).replace("\"filledSlots\":3", "\"filledSlots\":4") }
        val repo = repository(client)
        assertFailsWith<IllegalArgumentException> { repo.refreshForest() }
        assertFailsWith<IllegalStateException> { repo.getForest() }
        client.close()
    }

    @Test
    fun `중복된 슬롯이나 회차를 가진 나무 목록은 저장하지 않는다`() = runTest {
        for (trees in listOf(
            listOf(treeJson(1, slot = 7), treeJson(2, slot = 7)),
            listOf(treeJson(1, slot = 7), treeJson(1, slot = 8)),
        )) {
            val client = forestClient { forestBody(10, trees) }
            assertFailsWith<IllegalArgumentException> { repository(client).refreshForest() }
            client.close()
        }
    }

    @Test
    fun `토큰 만료는 공통 재발급 후 같은 숲 조회를 한 번 재시도한다`() = runTest {
        val tokens = ForestTestTokens()
        val original = tokens.value!!.accessToken
        val renewed = forestToken(1, "renewed")
        var queries = 0
        var reissues = 0
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/auth/reissue") {
                reissues++
                respond("""{"isSuccess":true,"code":"AUTH200","message":"OK","result":{"accessToken":"$renewed","refreshToken":"renewed-refresh"}}""", headers = forestHeaders)
            } else {
                queries++
                if (request.headers[HttpHeaders.Authorization] == "Bearer $original") {
                    respond("""{"isSuccess":false,"code":"AUTH401","message":"expired","result":null}""", HttpStatusCode.Unauthorized, forestHeaders)
                } else {
                    assertEquals("Bearer $renewed", request.headers[HttpHeaders.Authorization])
                    respond(forestBody(4), headers = forestHeaders)
                }
            }
        }) { configureRingoutHttpClient() }
        assertEquals(4, repository(client, tokens).refreshForest().growth.successfulDayCount)
        assertEquals(2, queries)
        assertEquals(1, reissues)
        client.close()
    }

    @Test
    fun `재발급 후에도 인증 오류이면 세션을 만료하고 반복 요청하지 않는다`() = runTest {
        val tokens = ForestTestTokens()
        val session = forestSession()
        var queries = 0
        var reissues = 0
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/auth/reissue") {
                reissues++
                respond("""{"isSuccess":true,"code":"AUTH200","message":"OK","result":{"accessToken":"${forestToken(1, "renewed")}","refreshToken":"renewed-refresh"}}""", headers = forestHeaders)
            } else {
                queries++
                respond("""{"isSuccess":false,"code":"AUTH401","message":"expired","result":null}""", HttpStatusCode.Unauthorized, forestHeaders)
            }
        }) { configureRingoutHttpClient() }
        assertFailsWith<ApiException> { repository(client, tokens, session).refreshForest() }
        assertEquals(2, queries)
        assertEquals(1, reissues)
        assertEquals(AuthSessionState.ReauthenticationRequired, session.state.value)
        client.close()
    }

    @Test
    fun `응답을 기다리는 중 계정이 바뀌면 이전 숲을 저장하지 않는다`() = runTest {
        val tokens = ForestTestTokens()
        val session = forestSession()
        val client = HttpClient(MockEngine {
            tokens.value = AuthTokens(forestToken(2), "other-refresh")
            session.startNewSession()
            respond(forestBody(23), headers = forestHeaders)
        }) { configureRingoutHttpClient() }
        val repo = repository(client, tokens, session)
        assertFailsWith<CancellationException> { repo.refreshForest() }
        assertFailsWith<IllegalStateException> { repo.getForest() }
        client.close()
    }

    @Test
    fun `늦게 완료된 이전 조회가 더 최근의 조회를 덮어쓰지 않는다`() = runTest {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var count = 0
        val client = HttpClient(MockEngine {
            if (++count == 1) {
                firstEntered.complete(Unit)
                releaseFirst.await()
                respond(forestBody(1), headers = forestHeaders)
            } else respond(forestBody(2), headers = forestHeaders)
        }) { configureRingoutHttpClient() }
        val repo = repository(client)
        val first = async { repo.refreshForest() }
        firstEntered.await()
        assertEquals(2, repo.refreshForest().growth.successfulDayCount)
        releaseFirst.complete(Unit)
        assertFailsWith<CancellationException> { first.await() }
        assertEquals(2, repo.getForest().growth.successfulDayCount)
        client.close()
    }

    @Test
    fun `취소된 조회는 나중에 성공해도 현재 숲을 바꾸지 않는다`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            entered.complete(Unit)
            gate.await()
            respond(forestBody(5), headers = forestHeaders)
        }) { configureRingoutHttpClient() }
        val repo = repository(client)
        val pending = async { repo.refreshForest() }
        entered.await()
        pending.cancel()
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertFailsWith<IllegalStateException> { repo.getForest() }
        client.close()
    }

    @Test
    fun `기기에서 완료한 온보딩과 나무 확인을 재조회와 저장소 재생성 후에도 보존한다`() = runTest {
        val store = MemoryForestProgress()
        var queries = 0
        val client = forestClient { queries++; forestBody(5, listOf(treeJson(1))) }
        val repo = repository(client, store = store)
        repo.refreshForest()
        repo.completeOnboarding(100)
        repo.acknowledgeTree(1, 200)
        assertEquals(1, queries)
        assertEquals(100L, repo.getForest().onboardingCompletedAtEpochMillis)
        assertEquals(200L, repo.refreshForest().trees.single().acknowledgedAtEpochMillis)
        val restored = repository(client, store = store).refreshForest()
        assertEquals(100L, restored.onboardingCompletedAtEpochMillis)
        assertEquals(200L, restored.trees.single().acknowledgedAtEpochMillis)
        assertEquals(3, queries)
        client.close()
    }

    @Test
    fun `서버에서 확인한 시각이 있으면 기기에 남은 확인 시각보다 우선한다`() = runTest {
        var serverConfirmed = false
        val time = "2026-11-10T08:00:00+09:00"
        val client = forestClient {
            val marker = if (serverConfirmed) "\"$time\"" else "null"
            forestBody(5, listOf(treeJson(1, acknowledged = marker)), marker)
        }
        val repo = repository(client)
        repo.refreshForest()
        repo.completeOnboarding(100)
        repo.acknowledgeTree(1, 200)
        serverConfirmed = true
        val state = repo.refreshForest()
        assertEquals(Instant.parse(time).toEpochMilliseconds(), state.onboardingCompletedAtEpochMillis)
        assertEquals(state.onboardingCompletedAtEpochMillis, state.trees.single().acknowledgedAtEpochMillis)
        client.close()
    }

    @Test
    fun `같은 회차라도 서버 나무의 종류 위치 획득시각이 달라지면 확인 상태를 재사용하지 않는다`() = runTest {
        var tree = treeJson(1)
        val client = forestClient { forestBody(5, listOf(tree)) }
        val repo = repository(client)
        repo.refreshForest()
        repo.completeOnboarding(100)
        repo.acknowledgeTree(1, 200)
        for (changed in listOf(
            treeJson(1, kind = "oak"),
            treeJson(1, slot = 9),
            treeJson(1).replace("2026-11-05", "2026-11-06"),
        )) {
            tree = changed
            assertNull(repo.refreshForest().trees.single().acknowledgedAtEpochMillis)
        }
        client.close()
    }

    @Test
    fun `다른 계정에서는 이전 계정의 숲과 기기 확인 상태를 사용하지 않는다`() = runTest {
        val tokens = ForestTestTokens()
        val session = forestSession()
        val client = forestClient { forestBody(5, listOf(treeJson(1))) }
        val repo = repository(client, tokens, session)
        repo.refreshForest()
        repo.completeOnboarding(100)
        repo.acknowledgeTree(1, 200)
        tokens.value = AuthTokens(forestToken(2), "other-refresh")
        session.startNewSession()
        assertFailsWith<IllegalStateException> { repo.getForest() }
        val state = repo.refreshForest()
        assertNull(state.onboardingCompletedAtEpochMillis)
        assertNull(state.trees.single().acknowledgedAtEpochMillis)
        client.close()
    }

    @Test
    fun `계정별 도착 전송 성공 횟수 변화만 관찰하고 조회는 시작하지 않는다`() = runTest {
        val tokens = ForestTestTokens()
        val session = forestSession()
        val first = MutableStateFlow(2)
        val second = MutableStateFlow(7)
        var queries = 0
        val client = forestClient { queries++; forestBody(0) }
        val repo = DefaultForestRepository(client, tokens, session, MemoryForestProgress()) { owner ->
            if (owner == "1") first else second
        }
        val counts = mutableListOf<Int>()
        backgroundScope.launch { repo.observeSuccessfulArrivalSyncs().collect { counts += it } }
        runCurrent()
        first.value = 3
        runCurrent()
        tokens.value = AuthTokens(forestToken(2), "other-refresh")
        session.startNewSession()
        runCurrent()
        first.value = 4
        second.value = 8
        runCurrent()
        assertEquals(listOf(2, 3, 7, 8), counts)
        assertEquals(0, queries)
        client.close()
    }

    @Test
    fun `로그인 식별자가 인증 상태보다 먼저 바뀌어도 도착 전송 관찰을 시작한다`() = runTest {
        val tokens = ForestTestTokens()
        val session = AuthSession().apply { clear() }
        val synced = MutableStateFlow(2)
        val client = forestClient { forestBody(0) }
        val repo = DefaultForestRepository(client, tokens, session, MemoryForestProgress()) { synced }
        val counts = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.observeSuccessfulArrivalSyncs().collect { counts += it }
        }
        assertTrue(counts.isEmpty())
        session.startNewSession()
        runCurrent()
        synced.value = 3
        runCurrent()
        assertEquals(listOf(2, 3), counts)
        client.close()
    }

    @Test
    fun `인증 상태에서 계정 토큰을 읽을 수 없으면 도착 기준값 대기를 오류로 종료한다`() = runTest {
        for (value in listOf(null, AuthTokens("invalid-access-token", "refresh"))) {
            val tokens = ForestTestTokens().apply { this.value = value }
            val client = forestClient { error("계정을 확인하기 전에 요청하지 않습니다.") }
            val repo = repository(client, tokens)
            assertFailsWith<IllegalStateException> { repo.observeSuccessfulArrivalSyncs().first() }
            client.close()
        }
    }

    @Test
    fun `로그아웃 후 이전 계정의 관찰 스냅샷을 다시 전달하지 않는다`() = runTest {
        val session = forestSession()
        val client = forestClient { forestBody(5) }
        val repo = repository(client, session = session)
        val states = mutableListOf<ForestState>()
        backgroundScope.launch { repo.observeForest().collect { states += it } }
        repo.refreshForest()
        runCurrent()
        session.clear()
        runCurrent()
        val count = states.size
        session.startNewSession()
        runCurrent()
        assertEquals(count, states.size)
        assertFailsWith<IllegalStateException> { repo.getForest() }
        client.close()
    }
}

private fun repository(
    client: HttpClient,
    tokens: ForestTestTokens = ForestTestTokens(),
    session: AuthSession = forestSession(),
    store: ForestLocalProgressStore = MemoryForestProgress(),
) = DefaultForestRepository(client, tokens, session, store) { flowOf(0) }

private fun forestSession() = AuthSession().apply { startNewSession() }
private val forestHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private fun forestClient(body: () -> String) = HttpClient(MockEngine {
    respond(body(), headers = forestHeaders)
}) { configureRingoutHttpClient() }

private fun forestToken(owner: Int, suffix: String = "original"): String =
    "header.${Base64.UrlSafe.encode("""{"userId":$owner,"sub":"$owner","tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=')}.$suffix"

private class ForestTestTokens : SecureTokenStorage {
    var value: AuthTokens? = AuthTokens(forestToken(1), "refresh")
    override suspend fun read() = value
    override suspend fun save(tokens: AuthTokens) { value = tokens }
    override suspend fun clear() { value = null }
}

private class MemoryForestProgress : ForestLocalProgressStore {
    private val progress = mutableMapOf<String, ForestLocalProgress>()
    override suspend fun read(ownerAccountId: String) = progress[ownerAccountId] ?: ForestLocalProgress()
    override suspend fun completeOnboarding(ownerAccountId: String, completedAtEpochMillis: Long) {
        val old = read(ownerAccountId)
        progress[ownerAccountId] = old.copy(onboardingCompletedAtEpochMillis = old.onboardingCompletedAtEpochMillis ?: completedAtEpochMillis)
    }
    override suspend fun acknowledgeTree(ownerAccountId: String, tree: ForestTree, acknowledgedAtEpochMillis: Long) {
        val old = read(ownerAccountId)
        progress[ownerAccountId] = old.copy(acknowledgedTrees = old.acknowledgedTrees +
            (tree.localProgressKey() to (old.acknowledgedTrees[tree.localProgressKey()] ?: acknowledgedAtEpochMillis)))
    }
}

private fun forestBody(days: Int, trees: List<String> = emptyList(), onboarding: String = "null"): String {
    val filled = if (days >= 80) 0 else days % 5
    val remaining = if (days >= 80) 0 else 5 - filled
    return """{"isSuccess":true,"code":"FOREST200","message":"숲 조회에 성공했습니다.","result":{"growth":{"successfulDayCount":$days,"filledSlots":$filled,"remainingDays":$remaining,"isComplete":${days >= 80}},"onboardingCompletedAt":$onboarding,"trees":[${trees.joinToString()}]}}"""
}

private fun treeJson(cycle: Int, kind: String = "pine", slot: Int = 7, acknowledged: String = "null") =
    """{"growthCycle":$cycle,"kind":"$kind","slotIndex":$slot,"plantedAt":"2026-11-05T07:48:10+09:00","acknowledgedAt":$acknowledged}"""
