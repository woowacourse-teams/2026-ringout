package com.joon.ringout.presentation.forest

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.forest.ForestGrowth
import com.joon.ringout.domain.forest.ForestRepository
import com.joon.ringout.domain.forest.ForestOnboardingSyncState
import com.joon.ringout.domain.forest.ForestPlantingSyncState
import com.joon.ringout.domain.forest.ForestTreeAcknowledgementSyncState
import com.joon.ringout.domain.forest.ForestState
import com.joon.ringout.domain.forest.ForestTree
import com.joon.ringout.domain.forest.ForestTreeKind
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.put
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

    @Test
    fun `온보딩을 로컬에 저장하면 서버 동기화 대기 중에도 웹에 성공을 전달한다`() = runTest {
        val fixture = Fixture(this)
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        fixture.connection.receive(ForestProtocol.message("forest.completeOnboarding", "complete"))
        runCurrent()
        val result = fixture.messages.single { it["requestId"]?.jsonPrimitive?.content == "complete" }
        assertTrue(result["success"]!!.jsonPrimitive.boolean)
        assertNotNull(fixture.repository.state.value.onboardingCompletedAtEpochMillis)
        assertEquals(ForestOnboardingSyncState.Pending, fixture.repository.onboardingSync.value)
        assertEquals(0, fixture.repository.onboardingRetryCount)
        assertEquals(1, fixture.repository.refreshCount)
    }

    @Test
    fun `숲 조회 성공과 실패가 온보딩 동기화 오류를 지우지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Blocked
        fixture.viewModel.setActive(true)
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
        fixture.repository.nextFetch = { error("offline") }
        fixture.viewModel.retry()
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.refreshErrorMessage)
        assertNotNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
        fixture.repository.nextFetch = { forest(5) }
        fixture.viewModel.retry()
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.refreshErrorMessage)
        assertNotNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Synced
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
    }

    @Test
    fun `온보딩 재시도는 중복 실행하거나 숲을 다시 조회하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Blocked
        fixture.viewModel.setActive(true)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        fixture.repository.nextOnboardingRetry = {
            gate.await()
            fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Pending
        }
        fixture.viewModel.retryOnboardingSync()
        fixture.viewModel.retryOnboardingSync()
        runCurrent()
        assertEquals(1, fixture.repository.onboardingRetryCount)
        assertTrue(fixture.viewModel.uiState.value.isRetryingOnboardingSync)
        assertEquals(1, fixture.repository.refreshCount)
        gate.complete(Unit)
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.isRetryingOnboardingSync)
        assertNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
        assertEquals(1, fixture.repository.refreshCount)
    }

    @Test
    fun `온보딩 재시도 기록 저장이 실패하면 완료 상태와 오류 안내를 유지한다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.state.value = forest(5).copy(onboardingCompletedAtEpochMillis = 100)
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Blocked
        fixture.repository.nextOnboardingRetry = { error("storage failed") }
        fixture.viewModel.setActive(true)
        runCurrent()
        fixture.viewModel.retryOnboardingSync()
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
        assertFalse(fixture.viewModel.uiState.value.isRetryingOnboardingSync)
        assertTrue(fixture.viewModel.uiState.value.hasSnapshot)
        assertEquals(100L, fixture.repository.state.value.onboardingCompletedAtEpochMillis)
    }

    @Test
    fun `숲이 가려진 동안 동기화 상태를 표시하지 않고 복귀하면 최신 상태를 표시한다`() = runTest {
        val fixture = Fixture(this)
        fixture.viewModel.setActive(true)
        runCurrent()
        fixture.viewModel.setActive(false)
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Blocked
        fixture.viewModel.retryOnboardingSync()
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
        assertEquals(0, fixture.repository.onboardingRetryCount)
        fixture.viewModel.setActive(true)
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
    }

    @Test
    fun `계정 변경 후 이전 온보딩 재시도 실패를 표시하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Blocked
        fixture.viewModel.setActive(true)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        fixture.repository.nextOnboardingRetry = {
            withContext(NonCancellable) { gate.await() }
            error("old account failure")
        }
        fixture.viewModel.retryOnboardingSync()
        runCurrent()
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Idle
        fixture.session.startNewSession()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
        assertFalse(fixture.viewModel.uiState.value.isRetryingOnboardingSync)
        assertEquals(1L, fixture.viewModel.uiState.value.sessionVersion)
    }

    @Test
    fun `나무 확인은 로컬 저장 직후 성공하고 서버 동기화는 별도 대기 상태로 남는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.state.value = forest(5).copy(
            onboardingCompletedAtEpochMillis = 1,
            trees = listOf(ForestTree(1, ForestTreeKind.Pine, 2, 50)),
        )
        fixture.viewModel.setActive(true)
        fixture.ready()
        runCurrent()
        fixture.connection.receive(ForestProtocol.message("forest.acknowledgeTree", "ack") { put("growthCycle", 1) })
        runCurrent()
        assertTrue(fixture.messages.single { it["requestId"]!!.jsonPrimitive.content == "ack" }["success"]!!.jsonPrimitive.boolean)
        assertNotNull(fixture.repository.state.value.trees.single().acknowledgedAtEpochMillis)
        assertEquals(ForestTreeAcknowledgementSyncState.Pending, fixture.repository.treeSync.value)
        assertEquals(0, fixture.repository.treeRetryCount)
        assertEquals(1, fixture.repository.refreshCount)
    }

    @Test
    fun `나무 동기화 오류는 조회와 온보딩 상태 변경과 독립적으로 유지된다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.treeSync.value = ForestTreeAcknowledgementSyncState.Blocked
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Blocked
        fixture.viewModel.setActive(true)
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
        assertNotNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
        fixture.repository.nextFetch = { error("offline") }
        fixture.viewModel.retry()
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.refreshErrorMessage)
        assertNotNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
        fixture.repository.nextFetch = { forest(5) }
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Synced
        fixture.viewModel.retry()
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.refreshErrorMessage)
        assertNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
        assertNotNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
        fixture.repository.treeSync.value = ForestTreeAcknowledgementSyncState.Synced
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
    }

    @Test
    fun `나무 동기화 재시도는 중복 호출을 합치고 일반 숲 갱신과 온보딩 재시도를 시작하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.treeSync.value = ForestTreeAcknowledgementSyncState.Blocked
        fixture.viewModel.setActive(true)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        fixture.repository.nextTreeRetry = {
            gate.await()
            fixture.repository.treeSync.value = ForestTreeAcknowledgementSyncState.Pending
        }
        fixture.viewModel.retryTreeSync()
        fixture.viewModel.retryTreeSync()
        runCurrent()
        assertEquals(1, fixture.repository.treeRetryCount)
        assertTrue(fixture.viewModel.uiState.value.isRetryingTreeSync)
        gate.complete(Unit)
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.isRetryingTreeSync)
        assertNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
        assertEquals(1, fixture.repository.refreshCount)
        assertEquals(0, fixture.repository.onboardingRetryCount)
    }

    @Test
    fun `나무 재시도 저장 실패에도 이미 확인한 나무와 숲 화면을 유지한다`() = runTest {
        val fixture = Fixture(this)
        val tree = ForestTree(1, ForestTreeKind.Pine, 2, 50, 100)
        fixture.repository.state.value = forest(5).copy(onboardingCompletedAtEpochMillis = 1, trees = listOf(tree))
        fixture.repository.treeSync.value = ForestTreeAcknowledgementSyncState.Blocked
        fixture.repository.nextTreeRetry = { error("storage failed") }
        fixture.viewModel.setActive(true)
        runCurrent()
        fixture.viewModel.retryTreeSync()
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
        assertFalse(fixture.viewModel.uiState.value.isRetryingTreeSync)
        assertTrue(fixture.viewModel.uiState.value.hasSnapshot)
        assertEquals(tree, fixture.repository.state.value.trees.single())
    }

    @Test
    fun `나무 동기화 관찰 실패는 안내를 표시하고 재시도에서 다시 관찰한다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.treeSyncFlow = flow { error("storage unavailable") }
        fixture.viewModel.setActive(true)
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
        fixture.repository.treeSyncFlow = fixture.repository.treeSync
        fixture.viewModel.retryTreeSync()
        runCurrent()
        assertEquals(1, fixture.repository.treeRetryCount)
        assertNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
    }

    @Test
    fun `화면이 가려지면 나무 오류 관찰과 수동 재시도를 멈추고 복귀 때 현재 상태를 읽는다`() = runTest {
        val fixture = Fixture(this)
        fixture.viewModel.setActive(true)
        runCurrent()
        fixture.viewModel.setActive(false)
        fixture.repository.treeSync.value = ForestTreeAcknowledgementSyncState.Blocked
        fixture.viewModel.retryTreeSync()
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
        assertEquals(0, fixture.repository.treeRetryCount)
        fixture.viewModel.setActive(true)
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
    }

    @Test
    fun `계정 변경 후 늦은 나무 재시도 실패는 새 계정 화면에 표시하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.treeSync.value = ForestTreeAcknowledgementSyncState.Blocked
        fixture.viewModel.setActive(true)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        fixture.repository.nextTreeRetry = {
            withContext(NonCancellable) { gate.await() }
            error("old account failure")
        }
        fixture.viewModel.retryTreeSync()
        runCurrent()
        fixture.repository.treeSync.value = ForestTreeAcknowledgementSyncState.Idle
        fixture.session.startNewSession()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
        assertFalse(fixture.viewModel.uiState.value.isRetryingTreeSync)
        assertEquals(1L, fixture.viewModel.uiState.value.sessionVersion)
    }

    @Test
    fun `식재 오류는 숲 조회와 온보딩 및 나무 확인 상태 변경으로 지워지지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.plantingSync.value = ForestPlantingSyncState.Blocked
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Blocked
        fixture.repository.treeSync.value = ForestTreeAcknowledgementSyncState.Blocked
        fixture.viewModel.setActive(true)
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
        fixture.repository.nextFetch = { error("offline") }
        fixture.viewModel.retry()
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.refreshErrorMessage)
        assertNotNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)

        fixture.repository.nextFetch = { forest(5) }
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Synced
        fixture.repository.treeSync.value = ForestTreeAcknowledgementSyncState.Synced
        fixture.viewModel.retry()
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.refreshErrorMessage)
        assertNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
        assertNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
        assertNotNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
        fixture.repository.plantingSync.value = ForestPlantingSyncState.Synced
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
    }

    @Test
    fun `식재 자동 전송 대기와 진행 상태는 오류 안내를 표시하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.plantingSync.value = ForestPlantingSyncState.Pending
        fixture.viewModel.setActive(true)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.hasSnapshot)
        assertNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
        fixture.repository.plantingSync.value = ForestPlantingSyncState.Syncing
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
        assertEquals(0, fixture.repository.plantingRetryCount)
        assertEquals(1, fixture.repository.refreshCount)
    }

    @Test
    fun `식재 재시도는 중복 실행을 합치고 다른 동기화와 일반 조회를 시작하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.plantingSync.value = ForestPlantingSyncState.Blocked
        fixture.repository.onboardingSync.value = ForestOnboardingSyncState.Blocked
        fixture.repository.treeSync.value = ForestTreeAcknowledgementSyncState.Blocked
        fixture.viewModel.setActive(true)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        fixture.repository.nextPlantingRetry = {
            gate.await()
            fixture.repository.plantingSync.value = ForestPlantingSyncState.Pending
        }
        fixture.viewModel.retryPlantingSync()
        fixture.viewModel.retryPlantingSync()
        runCurrent()
        assertEquals(1, fixture.repository.plantingRetryCount)
        assertTrue(fixture.viewModel.uiState.value.isRetryingPlantingSync)
        gate.complete(Unit)
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.isRetryingPlantingSync)
        assertNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
        assertNotNull(fixture.viewModel.uiState.value.onboardingSyncErrorMessage)
        assertNotNull(fixture.viewModel.uiState.value.treeSyncErrorMessage)
        assertEquals(1, fixture.repository.refreshCount)
        assertEquals(0, fixture.repository.onboardingRetryCount)
        assertEquals(0, fixture.repository.treeRetryCount)
    }

    @Test
    fun `식재 재시도 실패는 표시된 숲을 유지하고 다시 시도할 수 있게 한다`() = runTest {
        val fixture = Fixture(this)
        val planted = forest(5).copy(trees = listOf(ForestTree(1, ForestTreeKind.Oak, 3, 50)))
        fixture.repository.state.value = planted
        fixture.repository.plantingSync.value = ForestPlantingSyncState.Blocked
        fixture.repository.nextPlantingRetry = { error("storage failed") }
        fixture.viewModel.setActive(true)
        runCurrent()
        fixture.viewModel.retryPlantingSync()
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.hasSnapshot)
        assertFalse(fixture.viewModel.uiState.value.isRetryingPlantingSync)
        assertNotNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
        assertEquals(planted, fixture.repository.state.value)

        fixture.repository.nextPlantingRetry = { fixture.repository.plantingSync.value = ForestPlantingSyncState.Pending }
        fixture.viewModel.retryPlantingSync()
        runCurrent()
        assertEquals(2, fixture.repository.plantingRetryCount)
        assertNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
    }

    @Test
    fun `식재 상태 관찰이 실패하면 안내하고 수동 재시도로 관찰을 복구한다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.plantingSyncFlow = flow { error("storage unavailable") }
        fixture.viewModel.setActive(true)
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
        fixture.repository.plantingSyncFlow = fixture.repository.plantingSync
        fixture.viewModel.retryPlantingSync()
        runCurrent()
        assertEquals(1, fixture.repository.plantingRetryCount)
        assertNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
        assertEquals(1, fixture.repository.refreshCount)
    }

    @Test
    fun `화면이 가려진 동안 식재 오류 관찰과 수동 재시도를 멈추고 복귀하면 최신 상태를 표시한다`() = runTest {
        val fixture = Fixture(this)
        fixture.viewModel.setActive(true)
        runCurrent()
        fixture.viewModel.setActive(false)
        fixture.repository.plantingSync.value = ForestPlantingSyncState.Blocked
        fixture.viewModel.retryPlantingSync()
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
        assertEquals(0, fixture.repository.plantingRetryCount)
        fixture.viewModel.setActive(true)
        runCurrent()
        assertNotNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
    }

    @Test
    fun `계정 변경 후 늦은 식재 재시도 실패는 새 계정 화면에 표시하지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.plantingSync.value = ForestPlantingSyncState.Blocked
        fixture.viewModel.setActive(true)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        fixture.repository.nextPlantingRetry = {
            withContext(NonCancellable) { gate.await() }
            error("old account failure")
        }
        fixture.viewModel.retryPlantingSync()
        runCurrent()
        fixture.repository.plantingSync.value = ForestPlantingSyncState.Idle
        fixture.session.startNewSession()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertNull(fixture.viewModel.uiState.value.plantingSyncErrorMessage)
        assertFalse(fixture.viewModel.uiState.value.isRetryingPlantingSync)
        assertEquals(1L, fixture.viewModel.uiState.value.sessionVersion)
    }

    @Test
    fun `복귀 이전의 식재 재시도 종료가 복귀 후 진행 중인 재시도 상태를 지우지 않는다`() = runTest {
        val fixture = Fixture(this)
        fixture.repository.plantingSync.value = ForestPlantingSyncState.Blocked
        fixture.viewModel.setActive(true)
        runCurrent()
        val oldGate = CompletableDeferred<Unit>()
        fixture.repository.nextPlantingRetry = { withContext(NonCancellable) { oldGate.await() } }
        fixture.viewModel.retryPlantingSync()
        runCurrent()
        fixture.viewModel.setActive(false)
        assertFalse(fixture.viewModel.uiState.value.isRetryingPlantingSync)
        fixture.viewModel.setActive(true)
        runCurrent()
        val newGate = CompletableDeferred<Unit>()
        fixture.repository.nextPlantingRetry = { newGate.await() }
        fixture.viewModel.retryPlantingSync()
        runCurrent()
        oldGate.complete(Unit)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.isRetryingPlantingSync)
        assertEquals(2, fixture.repository.plantingRetryCount)
        newGate.complete(Unit)
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.isRetryingPlantingSync)
    }

    private class Fixture(scope: TestScope) {
        val repository = FakeRepository()
        val session = AuthSession().apply { markAuthenticated() }
        val viewModel = ForestViewModel(repository, scope.backgroundScope, session) { scope.testScheduler.currentTime }
        private val sentMessages = mutableListOf<JsonObject>()
        val messages get() = sentMessages.filter { it["type"]?.jsonPrimitive?.content != "forest.visibility" }
        var readyCount = 0
        var webReadyCount = 0
        var reloadCount = 0
        val connection = viewModel.connect(
            send = { sentMessages += Json.parseToJsonElement(it).jsonObject },
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
        val onboardingSync = MutableStateFlow(ForestOnboardingSyncState.Idle)
        var onboardingRetryCount = 0
        var nextOnboardingRetry: suspend () -> Unit = { onboardingSync.value = ForestOnboardingSyncState.Pending }
        val treeSync = MutableStateFlow(ForestTreeAcknowledgementSyncState.Idle)
        var treeSyncFlow: Flow<ForestTreeAcknowledgementSyncState> = treeSync
        var treeRetryCount = 0
        var nextTreeRetry: suspend () -> Unit = { treeSync.value = ForestTreeAcknowledgementSyncState.Pending }
        val plantingSync = MutableStateFlow(ForestPlantingSyncState.Idle)
        var plantingSyncFlow: Flow<ForestPlantingSyncState> = plantingSync
        var plantingRetryCount = 0
        var nextPlantingRetry: suspend () -> Unit = { plantingSync.value = ForestPlantingSyncState.Pending }
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
        override fun observeOnboardingSyncState() = onboardingSync
        override suspend fun retryOnboardingSync() {
            onboardingRetryCount++
            nextOnboardingRetry()
        }
        override fun observeTreeAcknowledgementSyncState() = treeSyncFlow
        override suspend fun retryTreeAcknowledgementSync() {
            treeRetryCount++
            nextTreeRetry()
        }
        override fun observePlantingSyncState() = plantingSyncFlow
        override suspend fun retryPlantingSync() {
            plantingRetryCount++
            nextPlantingRetry()
        }
        override suspend fun completeOnboarding(completedAtEpochMillis: Long) {
            state.value = state.value.copy(onboardingCompletedAtEpochMillis = completedAtEpochMillis)
            onboardingSync.value = ForestOnboardingSyncState.Pending
        }
        override suspend fun acknowledgeTree(growthCycle: Int, acknowledgedAtEpochMillis: Long) {
            state.value = state.value.copy(trees = state.value.trees.map {
                if (it.growthCycle == growthCycle) it.copy(acknowledgedAtEpochMillis = it.acknowledgedAtEpochMillis ?: acknowledgedAtEpochMillis) else it
            })
            treeSync.value = ForestTreeAcknowledgementSyncState.Pending
        }
    }
}

private fun forest(days: Int) = ForestState(ForestGrowth(days), emptyList(), null)
