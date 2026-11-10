package com.joon.ringout.presentation.forest

import com.joon.ringout.domain.forest.*
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
    private class Fixture(scope: TestScope) {
        val repo = FakeRepository()
        val messages = mutableListOf<JsonObject>()
        var readyCount = 0
        var backCount = 0
        private var id = 0
        val connection = ForestViewModel(repo, scope.backgroundScope) { 100 }.connect(
            send = { messages += Json.parseToJsonElement(it).jsonObject },
            onReady = { readyCount++ }, onBack = { backCount++ }, onError = {},
        )
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
            treeWrites++
            state.value = state.value.copy(trees = state.value.trees.map { if (it.growthCycle == growthCycle) it.copy(acknowledgedAtEpochMillis = it.acknowledgedAtEpochMillis ?: acknowledgedAtEpochMillis) else it })
        }
    }
    private companion object { val tree = ForestTree(1, ForestTreeKind.BigTree, 7, 50) }
}
