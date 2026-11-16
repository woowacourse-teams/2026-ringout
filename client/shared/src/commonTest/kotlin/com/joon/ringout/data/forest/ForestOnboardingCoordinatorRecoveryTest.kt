package com.joon.ringout.data.forest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForestOnboardingCoordinatorRecoveryTest {
    @Test
    fun `저장소 관찰이 실패해도 오초 후 다시 구독하고 새 완료 기록을 자동 전송한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val durableStore = DataStoreForestLocalProgressStore(RecoveryPreferences())
        val observedStore = FailingFirstObservationStore(durableStore)
        var putCount = 0
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                assertEquals(HttpMethod.Put, request.method)
                assertEquals("/api/v1/forest/onboarding", request.url.encodedPath)
                putCount++
                respond(
                    """{"isSuccess":true,"code":"FOREST201","message":"OK","result":{"onboardingCompletedAt":"2026-11-10T08:00:00+09:00"}}""",
                    HttpStatusCode.Created,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        })) { configureRingoutHttpClient() }
        val repository = DefaultForestRepository(client, RecoveryTokens(), session, observedStore) { flowOf(0) }
        val coordinator = ForestOnboardingSyncCoordinator(repository, session, RecoveryNetwork()) {
            testScheduler.currentTime
        }
        val job = coordinator.start(backgroundScope)

        try {
            runCurrent()
            assertEquals(1, observedStore.subscriptionCount)
            assertEquals(0, putCount)
            assertTrue(job.isActive)

            advanceTimeBy(4_999)
            runCurrent()
            assertEquals(1, observedStore.subscriptionCount)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, observedStore.subscriptionCount)
            assertEquals(0, putCount)

            // A new durable record must wake the recovered observer without a resume or manual retry.
            durableStore.completeOnboarding("1", 1_794_265_200_000L)
            runCurrent()

            assertEquals(1, putCount)
            assertEquals(ForestOnboardingDeliveryStatus.Synced, durableStore.read("1").onboardingSyncStatus)
            assertTrue(job.isActive)
        } finally {
            job.cancelAndJoin()
            client.close()
        }
    }
}

private class FailingFirstObservationStore(
    private val delegate: ForestLocalProgressStore,
) : ForestLocalProgressStore by delegate {
    var subscriptionCount = 0
        private set

    override fun observe(ownerAccountId: String): Flow<ForestLocalProgress> = flow {
        subscriptionCount++
        if (subscriptionCount == 1) throw IOException("일시적인 저장소 읽기 실패")
        emitAll(delegate.observe(ownerAccountId))
    }
}

private class RecoveryPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        transform(data.value).also { data.value = it }
    }
}

private class RecoveryTokens : SecureTokenStorage {
    private var tokens: AuthTokens? = AuthTokens(
        accessToken = "header.${Base64.UrlSafe.encode("""{"userId":1,"sub":"1","tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=')}.signature",
        refreshToken = "refresh",
    )

    override suspend fun read(): AuthTokens? = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}

private class RecoveryNetwork : NetworkMonitor {
    override val status = MutableStateFlow(NetworkStatus.Online)
}
