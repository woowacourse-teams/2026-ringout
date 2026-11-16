package com.joon.ringout.data.forest

import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceAccount
import com.joon.ringout.data.alarmoccurrence.captureAlarmOccurrenceAccount
import com.joon.ringout.data.alarmoccurrence.checkSession
import com.joon.ringout.data.alarmoccurrence.checkToken
import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.forest.ForestGrowth
import com.joon.ringout.domain.forest.ForestRepository
import com.joon.ringout.domain.forest.ForestState
import com.joon.ringout.domain.forest.ForestTree
import com.joon.ringout.domain.forest.ForestTreeKind
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Server growth and trees are authoritative. Only UI confirmations have a device-local overlay. */
class DefaultForestRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession,
    private val localProgressStore: ForestLocalProgressStore,
    private val observeSyncedArrivals: (String) -> Flow<Int>,
) : ForestRepository {
    private val requests = AuthenticatedRequestExecutor(httpClient, tokenStorage, authSession)
    private val snapshotMutex = Mutex()
    private val snapshot = MutableStateFlow<CachedForest?>(null)
    private var refreshSequence = 0L

    override suspend fun refreshForest(): ForestState {
        val account = currentAccount()
        val sequence = snapshotMutex.withLock {
            account.checkSession(authSession)
            ++refreshSequence
        }
        val response = requests.execute { accessToken ->
            account.checkToken(authSession, accessToken)
            httpClient.get(ApiConfig.url("/api/v1/forest")) { bearerAuth(accessToken) }
        }
        val text = response.bodyAsText()
        account.checkSession(authSession)
        if (!response.status.isSuccess()) {
            val error = runCatching { ApiJson.decodeFromString<ApiErrorResponse>(text) }.getOrNull()
            throw ApiException(response.status.value, error?.code, error?.message ?: response.status.description)
        }
        val body = ApiJson.decodeFromString<ApiResponse<ForestResponse>>(text)
        check(body.isSuccess) { body.message }
        val serverState = checkNotNull(body.result) { "숲 조회 응답이 비어 있어요." }.toDomain()
        return snapshotMutex.withLock {
            if (sequence != refreshSequence) throw CancellationException("더 최근의 숲 조회가 시작됐습니다.")
            publish(account, serverState)
        }
    }

    override suspend fun getForest(): ForestState {
        val account = currentAccount()
        return snapshotMutex.withLock { currentSnapshot(account).state }
    }

    override fun observeForest(): Flow<ForestState> = combine(
        authSession.identity,
        authSession.state,
        snapshot,
    ) { identity, sessionState, cached ->
        cached?.takeIf { sessionState == AuthSessionState.Authenticated &&
            authSession.state.value == AuthSessionState.Authenticated &&
            identity != null && identity === authSession.identity.value && it.account.sessionIdentity === identity }?.state
    }.filterNotNull().distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSuccessfulArrivalSyncs(): Flow<Int> = combine(
        authSession.identity,
        authSession.state,
    ) { identity, state -> identity to state }.flatMapLatest { (identity, state) ->
        flow {
            if (identity == null || state != AuthSessionState.Authenticated) return@flow
            val account = captureAlarmOccurrenceAccount(tokenStorage, authSession)
            if (account == null) {
                // A valid visible session must either produce its baseline or fail. Silently
                // completing here would leave the screen waiting forever before its first GET.
                check(authSession.identity.value !== identity || authSession.state.value != AuthSessionState.Authenticated) {
                    "로그인 정보를 확인할 수 없어요. 다시 시도해 주세요."
                }
                return@flow
            }
            if (account.sessionIdentity !== identity) return@flow
            observeSyncedArrivals(account.ownerAccountId).distinctUntilChanged().collect { count ->
                if (authSession.identity.value === identity && authSession.state.value == AuthSessionState.Authenticated) {
                    emit(count)
                }
            }
        }
    }

    override suspend fun completeOnboarding(completedAtEpochMillis: Long) {
        val account = currentAccount()
        snapshotMutex.withLock {
            val cached = currentSnapshot(account)
            if (!cached.state.isOnboardingCompleted) {
                localProgressStore.completeOnboarding(account.ownerAccountId, completedAtEpochMillis)
            }
            publish(account, cached.serverState)
        }
    }

    override suspend fun acknowledgeTree(growthCycle: Int, acknowledgedAtEpochMillis: Long) {
        val account = currentAccount()
        snapshotMutex.withLock {
            val cached = currentSnapshot(account)
            require(cached.state.isOnboardingCompleted)
            val tree = requireNotNull(cached.state.trees.find { it.growthCycle == growthCycle })
            if (tree.acknowledgedAtEpochMillis == null) {
                localProgressStore.acknowledgeTree(account.ownerAccountId, tree, acknowledgedAtEpochMillis)
            }
            publish(account, cached.serverState)
        }
    }

    private suspend fun currentAccount(): AlarmOccurrenceAccount =
        checkNotNull(captureAlarmOccurrenceAccount(tokenStorage, authSession)) { "로그인이 필요한 기능이에요." }

    private fun currentSnapshot(account: AlarmOccurrenceAccount): CachedForest {
        account.checkSession(authSession)
        return checkNotNull(snapshot.value?.takeIf { it.account == account }) { "숲 기록을 먼저 불러와 주세요." }
    }

    /** Called under snapshotMutex so a completed UI action cannot be rolled back by a refresh. */
    private suspend fun publish(account: AlarmOccurrenceAccount, serverState: ForestState): ForestState {
        val state = serverState.withLocalProgress(localProgressStore.read(account.ownerAccountId))
        currentCoroutineContext().ensureActive()
        account.checkSession(authSession)
        snapshot.value = CachedForest(account, serverState, state)
        return state
    }
}

private data class CachedForest(val account: AlarmOccurrenceAccount, val serverState: ForestState, val state: ForestState)

@Serializable
private data class ForestResponse(
    val growth: ForestGrowthResponse,
    val onboardingCompletedAt: String?,
    val trees: List<ForestTreeResponse>,
) {
    fun toDomain(): ForestState {
        val forestGrowth = ForestGrowth(growth.successfulDayCount)
        require(growth.filledSlots == forestGrowth.filledSlots &&
            growth.remainingDays == forestGrowth.remainingDays && growth.isComplete == forestGrowth.isComplete) {
            "숲 성장 응답을 확인할 수 없어요."
        }
        require(trees.size <= ForestGrowth.MaxTrees)
        require(trees.map { it.growthCycle }.distinct().size == trees.size)
        require(trees.map { it.slotIndex }.distinct().size == trees.size)
        return ForestState(
            growth = forestGrowth,
            onboardingCompletedAtEpochMillis = onboardingCompletedAt?.let { Instant.parse(it).toEpochMilliseconds() },
            trees = trees.map { it.toDomain() }.sortedBy { it.growthCycle },
        )
    }
}

@Serializable
private data class ForestGrowthResponse(
    val successfulDayCount: Int,
    val filledSlots: Int,
    val remainingDays: Int,
    val isComplete: Boolean,
)

@Serializable
private data class ForestTreeResponse(
    val growthCycle: Int,
    val kind: String,
    val slotIndex: Int,
    val plantedAt: String,
    val acknowledgedAt: String?,
) {
    fun toDomain() = ForestTree(
        growthCycle = growthCycle,
        kind = ForestTreeKind.fromPersistedValue(kind),
        slotIndex = slotIndex,
        plantedAtEpochMillis = Instant.parse(plantedAt).toEpochMilliseconds(),
        acknowledgedAtEpochMillis = acknowledgedAt?.let { Instant.parse(it).toEpochMilliseconds() },
    )
}
