package com.joon.ringout.presentation.forest

import com.joon.ringout.domain.forest.*
import com.joon.ringout.domain.auth.AuthSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ForestConnectionTest {
    @Test fun `준비 요청에 Room 상태를 전달하고 변경을 구독한다`() = runTest {
        val f = Fixture(this)
        f.request("forest.ready")
        runCurrent()
        assertEquals(1, f.readyCount)
        assertEquals(0, f.messages.last()["payload"]!!.jsonObject["growth"]!!.jsonObject["successfulDayCount"]!!.jsonPrimitive.int)
        f.repo.state.value = ForestState(ForestGrowth(5), listOf(tree), 1)
        runCurrent()
        assertEquals("big_tree", f.messages.last()["payload"]!!.jsonObject["trees"]!!.jsonArray.single().jsonObject["kind"]!!.jsonPrimitive.content)
    }
    @Test fun `온보딩을 저장한 결과로 응답하고 중복 요청은 한 번만 저장한다`() = runTest {
        val f = Fixture(this)
        f.request("forest.ready"); runCurrent()
        f.request("forest.completeOnboarding", "same"); f.request("forest.completeOnboarding", "same"); runCurrent()
        assertEquals(1, f.repo.onboardWrites)
        assertEquals(100L, f.repo.state.value.onboardingCompletedAtEpochMillis)
        assertTrue(f.messages.filter { it["requestId"]?.jsonPrimitive?.content == "same" }.all { it["success"]?.jsonPrimitive?.boolean == true })
    }
    @Test fun `나무 확인은 종류와 위치를 바꾸지 않고 확인 시각만 저장한다`() = runTest {
        val f = Fixture(this)
        f.repo.state.value = ForestState(ForestGrowth(5), listOf(tree), 1)
        f.request("forest.ready"); runCurrent()
        f.request("forest.acknowledgeTree", cycle = 1); runCurrent()
        assertEquals(tree.copy(acknowledgedAtEpochMillis = 100), f.repo.state.value.trees.single())
        f.request("forest.acknowledgeTree", cycle = 2); runCurrent()
        assertEquals("invalidRequest", f.messages.last()["error"]!!.jsonPrimitive.content)
        assertEquals(1, f.repo.treeWrites)
    }
    @Test fun `지원하지 않는 버전과 잘못된 회차는 저장하지 않는다`() = runTest {
        val f = Fixture(this)
        f.connection.receive("not json")
        f.connection.receive("""{"version":2,"type":"forest.ready","requestId":"old"}""")
        assertEquals("unsupportedVersion", f.messages.last()["error"]!!.jsonPrimitive.content)
        f.request("forest.acknowledgeTree", cycle = 17)
        assertEquals("invalidRequest", f.messages.last()["error"]!!.jsonPrimitive.content)
        f.request("forest.completeOnboarding"); runCurrent()
        assertEquals(0, f.repo.onboardWrites)
        assertEquals(0, f.repo.treeWrites)
    }
    @Test fun `같은 요청 번호를 다른 명령에 재사용하면 거절한다`() = runTest {
        val f = Fixture(this)
        f.request("forest.ready", "same"); runCurrent()
        f.request("forest.completeOnboarding", "same"); runCurrent()
        assertEquals("invalidRequest", f.messages.last()["error"]!!.jsonPrimitive.content)
        assertEquals(0, f.repo.onboardWrites)
    }
    @Test fun `저장 실패를 성공으로 알리지 않고 새 요청으로 재시도한다`() = runTest {
        val f = Fixture(this)
        f.request("forest.ready"); runCurrent()
        f.repo.failWrite = true
        f.request("forest.completeOnboarding"); runCurrent()
        assertEquals("storage", f.messages.last()["error"]!!.jsonPrimitive.content)
        assertNull(f.repo.state.value.onboardingCompletedAtEpochMillis)
        f.repo.failWrite = false
        f.request("forest.completeOnboarding"); runCurrent()
        assertEquals(100L, f.repo.state.value.onboardingCompletedAtEpochMillis)
    }
    @Test fun `페이지를 다시 열면 이전 비동기 응답을 전달하지 않는다`() = runTest {
        val f = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        f.repo.readGate = gate
        f.request("forest.ready"); runCurrent()
        f.connection.reset()
        gate.complete(Unit); runCurrent()
        assertTrue(f.messages.isEmpty())
        f.request("forest.ready"); runCurrent()
        assertEquals(1, f.readyCount)
        f.connection.close()
        f.repo.state.value = ForestState(ForestGrowth(2), emptyList(), null)
        val count = f.messages.size
        runCurrent()
        assertEquals(count, f.messages.size)
    }
    @Test fun `백그라운드에서 구독을 멈추고 복귀하면 최신 상태를 전달한다`() = runTest {
        val f = Fixture(this)
        f.request("forest.ready"); runCurrent()
        f.connection.setActive(false); runCurrent()
        val count = f.messages.size
        f.repo.state.value = ForestState(ForestGrowth(3), emptyList(), null)
        runCurrent(); assertEquals(count, f.messages.size)
        f.connection.setActive(true); runCurrent()
        assertEquals(3, f.messages.last()["payload"]!!.jsonObject["growth"]!!.jsonObject["filledSlots"]!!.jsonPrimitive.int)
    }
    @Test fun `시스템 뒤로 가기는 웹에 먼저 보내고 웹 요청으로 홈에 돌아간다`() = runTest {
        val f = Fixture(this)
        assertFalse(f.connection.back())
        f.request("forest.ready"); runCurrent()
        assertTrue(f.connection.back())
        assertEquals("forest.navigateBack", f.messages.last()["type"]!!.jsonPrimitive.content)
        assertEquals(0, f.backCount)
        f.request("forest.back"); runCurrent()
        assertEquals(1, f.backCount)
    }
    @Test fun `온보딩 완료는 명령이 대기한 뒤의 시각 대신 수신 시각을 저장한다`() = runTest {
        val f = Fixture(this)
        f.request("forest.ready")
        runCurrent()
        f.connection.setActive(false)
        f.time = 200
        f.request("forest.completeOnboarding")
        runCurrent()
        f.time = 900
        f.connection.setActive(true)
        runCurrent()
        assertEquals(200L, f.repo.state.value.onboardingCompletedAtEpochMillis)
    }

    @Test fun `숲 응답을 기다리는 동안에도 현재 페이지의 가시성을 먼저 전달한다`() = runTest {
        val f = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        f.repo.readGate = gate
        f.request("forest.ready", "page-ready")
        runCurrent()
        assertTrue(f.messages.isEmpty())
        assertEquals("page-ready", f.visibility.single()["requestId"]!!.jsonPrimitive.content)
        assertTrue(f.visibility.single()["active"]!!.jsonPrimitive.boolean)
        gate.complete(Unit)
        runCurrent()
        assertEquals("forest.visibility", f.sentMessages.first()["type"]!!.jsonPrimitive.content)
        assertEquals(1, f.readyCount)
    }

    @Test fun `백그라운드에서 준비한 페이지에는 비활성을 알리고 복귀 후 숲을 전달한다`() = runTest {
        val f = Fixture(this)
        f.connection.setActive(false)
        f.request("forest.ready", "hidden-page")
        runCurrent()
        assertFalse(f.visibility.single()["active"]!!.jsonPrimitive.boolean)
        assertTrue(f.messages.isEmpty())
        f.connection.setActive(true)
        runCurrent()
        assertEquals(listOf(false, true), f.visibility.map { it["active"]!!.jsonPrimitive.boolean })
        assertEquals(1, f.readyCount)
    }

    @Test fun `가시성 변경은 현재 페이지에만 보내고 같은 활성 상태를 반복 전송하지 않는다`() = runTest {
        val f = Fixture(this)
        f.request("forest.ready", "page-ready")
        runCurrent()
        f.connection.setActive(false)
        f.connection.setActive(false)
        f.connection.setActive(true)
        f.connection.setActive(true)
        runCurrent()
        assertEquals(listOf(true, false, true), f.visibility.map { it["active"]!!.jsonPrimitive.boolean })
        assertTrue(f.visibility.all { it["requestId"]!!.jsonPrimitive.content == "page-ready" })
    }

    @Test fun `같은 문서의 새 브리지는 새 준비 번호로 가시성을 받고 이전 중복 요청은 번호를 되돌리지 않는다`() = runTest {
        val f = Fixture(this)
        f.request("forest.ready", "old-bridge")
        runCurrent()
        f.request("forest.ready", "new-bridge")
        runCurrent()
        val count = f.visibility.size
        f.request("forest.ready", "old-bridge")
        runCurrent()
        assertEquals(count, f.visibility.size)
        f.connection.setActive(false)
        assertEquals("new-bridge", f.visibility.last()["requestId"]!!.jsonPrimitive.content)
        assertFalse(f.visibility.last()["active"]!!.jsonPrimitive.boolean)
    }

    @Test fun `같은 준비 요청의 재전송에는 현재 가시성을 다시 알린다`() = runTest {
        val f = Fixture(this)
        f.request("forest.ready", "same")
        runCurrent()
        f.connection.setActive(false)
        f.request("forest.ready", "same")
        runCurrent()
        assertEquals(listOf(true, false, false), f.visibility.map { it["active"]!!.jsonPrimitive.boolean })
        f.connection.setActive(true)
        runCurrent()
        assertEquals(1, f.readyCount)
    }

    @Test fun `페이지 초기화와 종료 뒤에는 이전 페이지의 가시성을 보내지 않는다`() = runTest {
        val f = Fixture(this)
        f.request("forest.ready", "old-page")
        runCurrent()
        f.connection.reset()
        val count = f.visibility.size
        f.connection.setActive(false)
        f.connection.setActive(true)
        assertEquals(count, f.visibility.size)
        f.request("forest.ready", "new-page")
        runCurrent()
        assertEquals("new-page", f.visibility.last()["requestId"]!!.jsonPrimitive.content)
        f.connection.close()
        val closedCount = f.sentMessages.size
        f.connection.setActive(false)
        f.connection.setActive(true)
        runCurrent()
        assertEquals(closedCount, f.sentMessages.size)
    }

    @Test fun `계정이 바뀌면 이전 페이지에 가시성을 전달하지 않는다`() = runTest {
        val f = Fixture(this)
        f.request("forest.ready")
        runCurrent()
        val count = f.sentMessages.size
        f.session.startNewSession()
        f.connection.setActive(false)
        f.connection.setActive(true)
        runCurrent()
        assertEquals(count, f.sentMessages.size)
    }

    @Test fun `나무 확인 시각은 활성화나 명령 잠금 대기 이전 수신 시각을 유지한다`() = runTest {
        val f = Fixture(this)
        f.repo.state.value = ForestState(ForestGrowth(5), listOf(tree), 1)
        f.request("forest.ready")
        runCurrent()
        f.connection.setActive(false)
        f.time = 200
        f.request("forest.acknowledgeTree", "tree-confirmed", 1)
        runCurrent()
        f.time = 900
        f.connection.setActive(true)
        runCurrent()
        assertEquals(200L, f.repo.state.value.trees.single().acknowledgedAtEpochMillis)
        assertTrue(f.messages.single { it["requestId"]!!.jsonPrimitive.content == "tree-confirmed" }["success"]!!.jsonPrimitive.boolean)
    }

    @Test fun `나무 확인의 로컬 저장 실패는 오류를 반환하고 재시도 전에는 미확인 상태를 유지한다`() = runTest {
        val f = Fixture(this)
        f.repo.state.value = ForestState(ForestGrowth(5), listOf(tree), 1)
        f.request("forest.ready")
        runCurrent()
        f.repo.failWrite = true
        f.request("forest.acknowledgeTree", "failed", 1)
        runCurrent()
        assertNull(f.repo.state.value.trees.single().acknowledgedAtEpochMillis)
        assertEquals("storage", f.messages.last()["error"]!!.jsonPrimitive.content)
        f.repo.failWrite = false
        f.request("forest.acknowledgeTree", "retry", 1)
        runCurrent()
        assertEquals(100L, f.repo.state.value.trees.single().acknowledgedAtEpochMillis)
        assertEquals(1, f.repo.treeWrites)
    }

    private class Fixture(scope: TestScope) {
        val repo = FakeRepository()
        val sentMessages = mutableListOf<JsonObject>()
        val messages get() = sentMessages.filter { it["type"]?.jsonPrimitive?.content != "forest.visibility" }
        val visibility get() = sentMessages.filter { it["type"]?.jsonPrimitive?.content == "forest.visibility" }
        var readyCount = 0
        var backCount = 0
        private var id = 0
        var time = 100L
        val session = AuthSession().apply { markAuthenticated() }
        private val viewModel = ForestViewModel(repo, scope.backgroundScope, session) { time }
        val connection = viewModel.connect(
            send = { sentMessages += Json.parseToJsonElement(it).jsonObject },
            onReady = { readyCount++ }, onBack = { backCount++ }, onError = {},
        )
        init { viewModel.setActive(true) }
        fun request(type: String, requestId: String = "id-${++id}", cycle: Int? = null) {
            connection.receive(ForestProtocol.message(type, requestId) { cycle?.let { put("growthCycle", it) } })
        }
    }
    private class FakeRepository : ForestRepository {
        val state = MutableStateFlow(ForestState(ForestGrowth(0), emptyList(), null))
        var onboardWrites = 0
        var treeWrites = 0
        var failWrite = false
        var readGate: CompletableDeferred<Unit>? = null
        override suspend fun getForest(): ForestState { readGate?.await(); return state.value }
        override fun observeForest() = state
        override suspend fun completeOnboarding(completedAtEpochMillis: Long) {
            if (failWrite) error("storage failure")
            onboardWrites++
            state.value = state.value.copy(onboardingCompletedAtEpochMillis = state.value.onboardingCompletedAtEpochMillis ?: completedAtEpochMillis)
        }
        override suspend fun acknowledgeTree(growthCycle: Int, acknowledgedAtEpochMillis: Long) {
            if (failWrite) error("storage failure")
            treeWrites++
            state.value = state.value.copy(trees = state.value.trees.map { if (it.growthCycle == growthCycle) it.copy(acknowledgedAtEpochMillis = it.acknowledgedAtEpochMillis ?: acknowledgedAtEpochMillis) else it })
        }
    }
    private companion object { val tree = ForestTree(1, ForestTreeKind.BigTree, 7, 50) }
}
