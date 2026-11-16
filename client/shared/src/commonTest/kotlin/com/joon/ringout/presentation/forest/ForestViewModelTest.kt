package com.joon.ringout.presentation.forest

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.forest.ForestGrowth
import com.joon.ringout.domain.forest.ForestRepository
import com.joon.ringout.domain.forest.ForestState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForestViewModelTest {
    @Test
    fun `화면이 보이기 전에는 조회하지 않고 최초 활성화와 준비 중복은 한 번만 조회한다`() = runTest {
        val fixture = Fixture(this)
        runCurrent()
        assertEquals(0, fixture.repository.refreshCount)
        fixture.viewModel.setActive(true)
        fixture.viewModel.setActive(true)
        fixture.ready("first")
        fixture.ready("first")
        runCurrent()
        assertEquals(1, fixture.repository.refreshCount)
        assertTrue(fixture.viewModel.uiState.value.hasSnapshot)
        assertEquals(1, fixture.readyCount)
    }

    @Test
    fun `웹이 먼저 준비되면 서버 응답까지 스냅샷과 준비 완료를 보류한다`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<ForestState>()
        fixture.repository.nextFetch = { gate.await() }
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        assertEquals(1, fixture.webReadyCount)
        assertTrue(fixture.messages.isEmpty())
        assertEquals(0, fixture.readyCount)
        assertTrue(fixture.viewModel.uiState.value.isLoading)
        gate.complete(forest(23))
        runCurrent()
        assertEquals(1, fixture.readyCount)
        assertEquals(23, fixture.lastDayCount())
    }

    @Test
    fun `서버 응답이 먼저 오면 웹 준비 전에는 전달하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.nextFetch = { forest(4) }
        fixture.viewModel.setActive(true)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.hasSnapshot)
        assertTrue(fixture.messages.isEmpty())
        fixture.ready()
        runCurrent()
        assertEquals(4, fixture.lastDayCount())
        assertEquals(1, fixture.repository.refreshCount)
    }

    @Test
    fun `페이지 재로딩과 웹 명령은 서버를 추가 조회하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        fixture.connection.reset()
        fixture.ready("reloaded")
        runCurrent()
        fixture.connection.receive(ForestProtocol.message("forest.completeOnboarding", "onboard"))
        runCurrent()
        assertEquals(1, fixture.repository.refreshCount)
        assertEquals(2, fixture.readyCount)
    }

    @Test
    fun `첫 조회 실패를 빈 숲으로 표시하지 않고 재시도로 복구한다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.nextFetch = { error("offline") }
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.hasSnapshot)
        assertNotNull(fixture.viewModel.uiState.value.errorMessage)
        assertTrue(fixture.messages.isEmpty())
        fixture.repository.nextFetch = { forest(5) }
        fixture.viewModel.retry()
        runCurrent()
        assertEquals(2, fixture.repository.refreshCount)
        assertNull(fixture.viewModel.uiState.value.errorMessage)
        assertEquals(5, fixture.lastDayCount())
    }

    @Test
    fun `앱 복귀는 한 번 갱신하고 실패해도 표시한 숲을 유지한다`() = runTest {
        val fixture = Fixture(this)
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        fixture.viewModel.setActive(false)
        fixture.connection.setActive(false)
        fixture.repository.arrivals.value = 1
        runCurrent()
        assertEquals(1, fixture.repository.refreshCount)
        fixture.repository.nextFetch = { error("offline") }
        fixture.viewModel.setActive(true)
        fixture.viewModel.setActive(true)
        fixture.connection.setActive(true)
        runCurrent()
        assertEquals(2, fixture.repository.refreshCount)
        assertTrue(fixture.viewModel.uiState.value.hasSnapshot)
        assertFalse(fixture.viewModel.uiState.value.isLoading)
        assertNull(fixture.viewModel.uiState.value.errorMessage)
        assertNotNull(fixture.viewModel.uiState.value.refreshErrorMessage)
        fixture.repository.nextFetch = { forest(6) }
        fixture.viewModel.retry()
        runCurrent()
        assertEquals(3, fixture.repository.refreshCount)
        assertNull(fixture.viewModel.uiState.value.refreshErrorMessage)
        assertEquals(6, fixture.lastDayCount())
    }

    @Test
    fun `조회 중 여러 도착 전송이 완료되면 후속 조회를 한 번 수행한다`() = runTest {
        val fixture = Fixture(this)
        val first = CompletableDeferred<ForestState>()
        fixture.repository.nextFetch = { first.await() }
        fixture.viewModel.setActive(true)
        runCurrent()
        fixture.repository.arrivals.value = 1
        runCurrent()
        fixture.repository.arrivals.value = 2
        runCurrent()
        fixture.viewModel.setActive(true)
        fixture.viewModel.retry()
        runCurrent()
        assertEquals(1, fixture.repository.refreshCount)
        fixture.repository.nextFetch = { forest(2) }
        first.complete(forest(0))
        runCurrent()
        assertEquals(2, fixture.repository.refreshCount)
        assertEquals(2, fixture.repository.state.value.growth.successfulDayCount)
    }

    @Test
    fun `대기 요청이 없을 때 도착 전송 완료만 추가 조회를 유발한다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.arrivals.value = 7
        fixture.viewModel.setActive(true)
        runCurrent()
        assertEquals(1, fixture.repository.refreshCount)
        fixture.repository.arrivals.value = 7
        runCurrent()
        assertEquals(1, fixture.repository.refreshCount)
        fixture.repository.arrivals.value = 8
        runCurrent()
        assertEquals(2, fixture.repository.refreshCount)
    }

    @Test
    fun `화면 종료 후 취소를 늦게 처리한 응답은 UI를 완료하지 않는다`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<ForestState>()
        fixture.repository.nextFetch = { withContext(NonCancellable) { gate.await() } }
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        fixture.viewModel.setActive(false)
        fixture.connection.close()
        gate.complete(forest(10))
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.hasSnapshot)
        assertTrue(fixture.messages.isEmpty())
        fixture.repository.arrivals.value = 2
        runCurrent()
        assertEquals(1, fixture.repository.refreshCount)
    }

    @Test
    fun `계정이 바뀌면 기존 숲을 지우고 이전 페이지에 응답하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        val count = fixture.messages.size
        fixture.repository.nextFetch = { error("new account offline") }
        fixture.session.startNewSession()
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.hasSnapshot)
        assertEquals(1L, fixture.viewModel.uiState.value.sessionVersion)
        assertNotNull(fixture.viewModel.uiState.value.errorMessage)
        fixture.ready("old-page")
        runCurrent()
        assertEquals(count, fixture.messages.size)
    }

    @Test
    fun `재인증이 필요하면 기존 숲과 갱신 요청을 중단한다`() = runTest {
        val fixture = Fixture(this)
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        fixture.session.requireReauthentication()
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.hasSnapshot)
        assertNotNull(fixture.viewModel.uiState.value.errorMessage)
        fixture.viewModel.retry()
        fixture.repository.arrivals.value = 5
        runCurrent()
        assertEquals(1, fixture.repository.refreshCount)
    }

    @Test
    fun `준비 요청 만료 후 응답은 페이지를 다시 준비하고 API는 재요청하지 않는다`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<ForestState>()
        fixture.repository.nextFetch = { gate.await() }
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()
        gate.complete(forest(5))
        runCurrent()
        assertEquals(1, fixture.reloadCount)
        assertEquals(0, fixture.readyCount)
        assertTrue(fixture.messages.isEmpty())
        fixture.connection.reset()
        fixture.ready("fresh-ready")
        runCurrent()
        assertEquals(1, fixture.readyCount)
        assertEquals(1, fixture.repository.refreshCount)
        assertEquals(5, fixture.lastDayCount())
    }

    @Test
    fun `조회 오류 후 늦은 재시도는 만료된 웹 준비 응답을 재사용하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.nextFetch = { error("offline") }
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()
        fixture.repository.nextFetch = { forest(0) }
        fixture.viewModel.retry()
        runCurrent()
        assertEquals(1, fixture.reloadCount)
        assertEquals(0, fixture.readyCount)
        assertEquals(2, fixture.repository.refreshCount)
        fixture.connection.reset()
        fixture.ready("fresh-ready")
        runCurrent()
        assertEquals(1, fixture.readyCount)
        assertEquals(2, fixture.repository.refreshCount)
    }

    @Test
    fun `첫 준비 중 오래 백그라운드에 머물면 복귀 응답으로 새 페이지를 준비한다`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<ForestState>()
        fixture.repository.nextFetch = { gate.await() }
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        fixture.viewModel.setActive(false)
        fixture.connection.setActive(false)
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(0, fixture.reloadCount)
        fixture.repository.nextFetch = { forest(1) }
        fixture.connection.setActive(true)
        fixture.viewModel.setActive(true)
        runCurrent()
        assertEquals(1, fixture.reloadCount)
        assertEquals(0, fixture.readyCount)
        assertEquals(2, fixture.repository.refreshCount)
    }

    @Test
    fun `조회 시간 초과 후 재시도할 수 있다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.nextFetch = { CompletableDeferred<ForestState>().await() }
        fixture.viewModel.setActive(true)
        runCurrent()
        advanceTimeBy(20_001)
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.isLoading)
        assertNotNull(fixture.viewModel.uiState.value.errorMessage)
        fixture.repository.nextFetch = { forest(0) }
        fixture.viewModel.retry()
        runCurrent()
        assertEquals(2, fixture.repository.refreshCount)
        assertTrue(fixture.viewModel.uiState.value.hasSnapshot)
    }

    @Test
    fun `도착 관찰 오류가 먼저 발생해도 조회 성공은 숲을 표시하고 관찰을 재시도한다`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<ForestState>()
        val observationFailure = CompletableDeferred<Unit>()
        fixture.repository.arrivalFlow = flow {
            emit(0)
            observationFailure.await()
            error("database unavailable")
        }
        fixture.repository.nextFetch = { gate.await() }
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        assertEquals(1, fixture.repository.refreshCount)
        observationFailure.complete(Unit)
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.errorMessage)
        gate.complete(forest(5))
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.hasSnapshot)
        assertNull(fixture.viewModel.uiState.value.errorMessage)
        assertNotNull(fixture.viewModel.uiState.value.refreshErrorMessage)
        assertEquals(1, fixture.readyCount)
        fixture.repository.arrivalFlow = fixture.repository.arrivals
        fixture.repository.nextFetch = { forest(6) }
        fixture.viewModel.retry()
        runCurrent()
        assertEquals(2, fixture.repository.refreshCount)
        assertNull(fixture.viewModel.uiState.value.refreshErrorMessage)
        fixture.repository.arrivals.value = 1
        runCurrent()
        assertEquals(3, fixture.repository.refreshCount)
    }

    @Test
    fun `백그라운드에서 기다리던 중복 준비 응답은 계정 변경 후 전달하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.viewModel.setActive(true)
        fixture.ready("same")
        runCurrent()
        fixture.connection.setActive(false)
        fixture.ready("same")
        runCurrent()
        val count = fixture.messages.size
        fixture.session.startNewSession()
        fixture.connection.setActive(true)
        runCurrent()
        assertEquals(count, fixture.messages.size)
    }

    @Test
    fun `준비 중 현재 숲 읽기가 지연돼도 만료된 웹 응답을 전달하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.viewModel.setActive(true)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        fixture.repository.readGate = gate
        fixture.ready()
        runCurrent()
        fixture.connection.setActive(false)
        advanceTimeBy(10_001)
        runCurrent()
        gate.complete(Unit)
        fixture.connection.setActive(true)
        runCurrent()
        assertEquals(1, fixture.reloadCount)
        assertEquals(0, fixture.readyCount)
        assertTrue(fixture.messages.isEmpty())
        fixture.connection.reset()
        fixture.ready("fresh-ready")
        runCurrent()
        assertEquals(1, fixture.readyCount)
        assertEquals(1, fixture.repository.refreshCount)
    }

    private class Fixture(scope: TestScope) {
        val repository = FakeRepository()
        val session = AuthSession().apply { markAuthenticated() }
        val viewModel = ForestViewModel(repository, scope.backgroundScope, session) { scope.testScheduler.currentTime }
        val messages = mutableListOf<JsonObject>()
        var readyCount = 0
        var webReadyCount = 0
        var reloadCount = 0
        val connection = viewModel.connect(
            send = { messages += Json.parseToJsonElement(it).jsonObject },
            onReady = { readyCount++ },
            onBack = {},
            onError = { error("unexpected bridge failure") },
            onWebReady = { webReadyCount++ },
            onReloadRequired = { reloadCount++ },
        )
        fun ready(id: String = "ready") = connection.receive(ForestProtocol.message("forest.ready", id))
        fun lastDayCount() = messages.last()["payload"]!!.jsonObject["growth"]!!.jsonObject["successfulDayCount"]!!.jsonPrimitive.int
    }

    private class FakeRepository : ForestRepository {
        val state = MutableStateFlow(forest(0))
        val arrivals = MutableStateFlow(0)
        var arrivalFlow: Flow<Int> = arrivals
        var refreshCount = 0
        var nextFetch: suspend () -> ForestState = { state.value }
        var readGate: CompletableDeferred<Unit>? = null
        override suspend fun refreshForest(): ForestState {
            refreshCount++
            return nextFetch().also { state.value = it }
        }
        override fun observeSuccessfulArrivalSyncs() = arrivalFlow
        override suspend fun getForest(): ForestState {
            readGate?.await()
            return state.value
        }
        override fun observeForest() = state
        override suspend fun completeOnboarding(completedAtEpochMillis: Long) {
            state.value = state.value.copy(onboardingCompletedAtEpochMillis = completedAtEpochMillis)
        }
        override suspend fun acknowledgeTree(growthCycle: Int, acknowledgedAtEpochMillis: Long) = Unit
    }
}

private fun forest(days: Int) = ForestState(ForestGrowth(days), emptyList(), null)
