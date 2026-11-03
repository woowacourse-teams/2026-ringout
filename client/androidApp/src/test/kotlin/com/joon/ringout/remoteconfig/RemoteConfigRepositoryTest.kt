package com.joon.ringout.remoteconfig

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteConfigRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `저장된 설정을 읽기 전에는 초기화가 완료되지 않는다`() {
        val defaultsGate = CompletableDeferred<Unit>()
        val backend = FakeRemoteConfigBackend().apply {
            this.defaultsGate = defaultsGate
            active = remoteSnapshot("42", 123L)
            gate = CompletableDeferred()
        }

        val repository = RemoteConfigRepository(backend, scope)

        assertFalse(repository.state.value.isInitialized)
        assertTrue(backend.fetchArguments.isEmpty())
        defaultsGate.complete(Unit)
        assertTrue(repository.state.value.isInitialized)
        assertEquals(42, repository.state.value.minVersionCode)
        assertEquals(RemoteConfigFetchStatus.FETCHING, repository.state.value.fetchStatus)
    }

    @Test
    fun `네트워크 응답이 끝나기 전에도 저장된 설정으로 버전을 판단할 수 있다`() {
        val backend = FakeRemoteConfigBackend().apply {
            active = remoteSnapshot("42", 123L)
            gate = CompletableDeferred()
        }

        val repository = RemoteConfigRepository(backend, scope)

        assertTrue(repository.state.value.isInitialized)
        assertEquals(42, repository.state.value.minVersionCode)
        assertEquals(RemoteConfigValueSource.REMOTE, repository.state.value.valueSource)
        assertEquals(RemoteConfigFetchStatus.FETCHING, repository.state.value.fetchStatus)
    }

    @Test
    fun `로컬 설정 초기화가 실패해도 기본값으로 초기화를 완료한다`() {
        val backend = FakeRemoteConfigBackend().apply { defaultsFail = true }

        val repository = RemoteConfigRepository(backend, scope)

        assertTrue(repository.state.value.isInitialized)
        assertEquals(1, repository.state.value.minVersionCode)
        assertEquals(RemoteConfigValueSource.DEFAULT, repository.state.value.valueSource)
        assertEquals(RemoteConfigFetchStatus.FAILURE, repository.state.value.fetchStatus)
    }

    @Test
    fun `첫 실행에 설정을 가져오지 못하면 기본값과 실패 상태를 표시한다`() {
        val backend = FakeRemoteConfigBackend().apply { fetchFails = true }

        val repository = RemoteConfigRepository(backend, scope)

        assertEquals(1, repository.state.value.minVersionCode)
        assertEquals(RemoteConfigValueSource.DEFAULT, repository.state.value.valueSource)
        assertEquals(RemoteConfigFetchStatus.FAILURE, repository.state.value.fetchStatus)
        assertEquals(null, repository.state.value.lastFetchTimeMillis)
    }

    @Test
    fun `이전에 활성화한 원격 설정은 오프라인 갱신에 실패해도 유지된다`() {
        val backend = FakeRemoteConfigBackend().apply {
            active = remoteSnapshot("42", 123L)
            fetchFails = true
        }

        val repository = RemoteConfigRepository(backend, scope)

        assertEquals(42, repository.state.value.minVersionCode)
        assertEquals(RemoteConfigValueSource.REMOTE, repository.state.value.valueSource)
        assertEquals(123L, repository.state.value.lastFetchTimeMillis)
        assertEquals(RemoteConfigFetchStatus.FAILURE, repository.state.value.fetchStatus)
    }

    @Test
    fun `양의 정수가 아닌 원격 값은 기본값으로 대체한다`() {
        listOf("0", "-1", "1.5", "abc", "2147483648", "").forEach { invalid ->
            val backend = FakeRemoteConfigBackend().apply {
                active = remoteSnapshot(invalid, 123L)
            }

            val repository = RemoteConfigRepository(backend, scope)

            assertEquals("입력: $invalid", 1, repository.state.value.minVersionCode)
            assertEquals(RemoteConfigValueSource.INVALID_REMOTE, repository.state.value.valueSource)
        }
    }

    @Test
    fun `수동 갱신은 새 값을 활성화하고 최근 성공 시각을 표시한다`() {
        val backend = FakeRemoteConfigBackend().apply { active = remoteSnapshot("42", 123L) }
        val repository = RemoteConfigRepository(backend, scope)
        backend.next = remoteSnapshot("99", 456L)

        repository.refresh()

        assertEquals(listOf(false, true), backend.fetchArguments)
        assertEquals(99, repository.state.value.minVersionCode)
        assertEquals(RemoteConfigValueSource.REMOTE, repository.state.value.valueSource)
        assertEquals(456L, repository.state.value.lastFetchTimeMillis)
        assertEquals(RemoteConfigFetchStatus.SUCCESS, repository.state.value.fetchStatus)
    }

    @Test
    fun `실시간 갱신은 가져온 설정을 활성화하여 구독 중인 값에 반영한다`() {
        val backend = FakeRemoteConfigBackend().apply { active = remoteSnapshot("42", 123L) }
        val repository = RemoteConfigRepository(backend, scope)
        backend.next = remoteSnapshot("99", 456L)

        backend.emitUpdate()

        assertEquals(99, repository.state.value.minVersionCode)
        assertEquals(456L, repository.state.value.lastFetchTimeMillis)
        assertEquals(listOf(false), backend.fetchArguments)
    }

    @Test
    fun `실시간 리스너 오류는 기존 설정을 유지하고 다음 갱신에 해제된다`() {
        val backend = FakeRemoteConfigBackend().apply { active = remoteSnapshot("42", 123L) }
        val repository = RemoteConfigRepository(backend, scope)

        backend.emitError()

        assertTrue(repository.state.value.listenerError)
        assertEquals(42, repository.state.value.minVersionCode)
        backend.next = remoteSnapshot("99", 456L)
        backend.emitUpdate()
        assertFalse(repository.state.value.listenerError)
        assertEquals(99, repository.state.value.minVersionCode)
    }

    @Test
    fun `시작 시 가져오는 동안 실시간 갱신이 도착하면 활성화를 순서대로 처리한다`() {
        val fetchGate = CompletableDeferred<Unit>()
        val backend = FakeRemoteConfigBackend().apply {
            active = remoteSnapshot("42", 123L)
            gate = fetchGate
        }
        val repository = RemoteConfigRepository(backend, scope)
        backend.next = remoteSnapshot("99", 456L)

        backend.emitUpdate()

        assertEquals(0, backend.activationCount)
        assertEquals(RemoteConfigFetchStatus.FETCHING, repository.state.value.fetchStatus)
        fetchGate.complete(Unit)
        assertEquals(2, backend.activationCount)
        assertEquals(99, repository.state.value.minVersionCode)
        assertFalse(backend.activationDuringFetch)
        assertTrue(backend.defaultsSet)
    }

    private class FakeRemoteConfigBackend : RemoteConfigBackend {
        var active = RemoteConfigSnapshot("1", false, null, RemoteConfigFetchStatus.NOT_FETCHED)
        var next: RemoteConfigSnapshot? = null
        var fetchFails = false
        var defaultsFail = false
        var defaultsGate: CompletableDeferred<Unit>? = null
        var gate: CompletableDeferred<Unit>? = null
        var activationCount = 0
        var activationDuringFetch = false
        var defaultsSet = false
        val fetchArguments = mutableListOf<Boolean>()
        private var fetching = false
        private lateinit var onUpdate: () -> Unit
        private lateinit var onError: () -> Unit

        override suspend fun setDefaults() {
            defaultsGate?.await()
            check(!defaultsFail) { "local config initialization failed" }
            defaultsSet = true
        }

        override suspend fun fetch(force: Boolean) {
            fetchArguments.add(force)
            fetching = true
            try {
                gate?.await()
                check(!fetchFails) { "offline" }
            } finally {
                fetching = false
            }
        }

        override suspend fun activate() {
            activationDuringFetch = activationDuringFetch || fetching
            activationCount++
            next?.let { active = it }
            next = null
        }

        override fun snapshot(): RemoteConfigSnapshot = active

        override fun listen(onUpdate: () -> Unit, onError: () -> Unit) {
            this.onUpdate = onUpdate
            this.onError = onError
        }

        fun emitUpdate() = onUpdate()

        fun emitError() = onError()
    }

    companion object {
        private fun remoteSnapshot(value: String, lastFetchTime: Long) = RemoteConfigSnapshot(
            rawValue = value,
            isRemote = true,
            lastFetchTimeMillis = lastFetchTime,
            fetchStatus = RemoteConfigFetchStatus.SUCCESS,
        )
    }
}
