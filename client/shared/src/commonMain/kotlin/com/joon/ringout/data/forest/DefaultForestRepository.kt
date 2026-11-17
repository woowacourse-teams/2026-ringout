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
import com.joon.ringout.domain.forest.ForestOnboardingSyncState
import com.joon.ringout.domain.forest.ForestPlantingPolicy
import com.joon.ringout.domain.forest.ForestPlantingSyncState
import com.joon.ringout.domain.forest.ForestRepository
import com.joon.ringout.domain.forest.ForestState
import com.joon.ringout.domain.forest.ForestTree
import com.joon.ringout.domain.forest.ForestTreeAcknowledgementSyncState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.io.IOException
import kotlin.time.Instant

/** Server growth and placements are authoritative; durable work survives the displaying page. */
class DefaultForestRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession,
    private val localProgressStore: ForestLocalProgressStore,
    private val observeSyncedArrivals: (String) -> Flow<Int>,
) : ForestRepository, ForestArrivalPlanting {
    private val requests = AuthenticatedRequestExecutor(httpClient, tokenStorage, authSession)
    private val onboardingRemote = ForestOnboardingRemoteDataSource(httpClient, tokenStorage, authSession)
    private val treeRemote = ForestTreeRemoteDataSource(httpClient, tokenStorage, authSession)
    private val snapshotMutex = Mutex()
    private val snapshot = MutableStateFlow<CachedForest?>(null)
    private var refreshSequence = 0L
    private var remoteReadSequence = 0L
    private var publishedReadSequence = 0L
    private var arrivalSessionIdentity: Any? = null
    private var arrivalMutex = Mutex()
    private var deliverySessionIdentity: Any? = null
    private var deliveryMutex = Mutex()
    private var unauthorizedSessionIdentity: Any? = null
    private val sendingOnboarding = MutableStateFlow<AlarmOccurrenceAccount?>(null)
    private val sendingTree = MutableStateFlow<AlarmOccurrenceAccount?>(null)
    private val sendingPlanting = MutableStateFlow<AlarmOccurrenceAccount?>(null)
    private val evaluatingArrival = MutableStateFlow<AlarmOccurrenceAccount?>(null)

    override suspend fun refreshForest(): ForestState {
        val account = currentAccount()
        val (sequence, readSequence, missingBeforeRequest) = snapshotMutex.withLock {
            account.checkSession(authSession)
            Triple(++refreshSequence, ++remoteReadSequence, missingTreeDeliveries(localProgressStore.read(account.ownerAccountId)))
        }
        val serverState = fetchRemoteForest(account)
        return snapshotMutex.withLock {
            account.checkSession(authSession)
            if (sequence != refreshSequence) throw CancellationException("더 최근의 숲 조회가 시작됐습니다.")
            publishFresh(account, serverState, readSequence, missingBeforeRequest)
        }
    }

    private suspend fun fetchRemoteForest(account: AlarmOccurrenceAccount): ForestState {
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
        return checkNotNull(body.result) { "숲 조회 응답이 비어 있어요." }.toDomain()
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

    override fun observeOnboardingSyncState(): Flow<ForestOnboardingSyncState> = combine(
        observeOnboardingWork(), sendingOnboarding,
    ) { work, sending ->
        when {
            work == null || work.account.sessionIdentity !== authSession.identity.value ||
                authSession.state.value != AuthSessionState.Authenticated -> ForestOnboardingSyncState.Idle
            work.progress.onboardingCompletedAtEpochMillis == null -> ForestOnboardingSyncState.Idle
            work.progress.onboardingSyncStatus == ForestOnboardingDeliveryStatus.Synced -> ForestOnboardingSyncState.Synced
            sending == work.account -> ForestOnboardingSyncState.Syncing
            work.progress.onboardingSyncStatus == ForestOnboardingDeliveryStatus.Blocked -> ForestOnboardingSyncState.Blocked
            else -> ForestOnboardingSyncState.Pending
        }
    }.distinctUntilChanged()

    override suspend fun retryOnboardingSync() {
        val account = currentAccount()
        snapshotMutex.withLock {
            account.checkSession(authSession)
            localProgressStore.update(account.ownerAccountId) { progress ->
                account.checkSession(authSession)
                if (progress.onboardingCompletedAtEpochMillis == null ||
                    progress.onboardingSyncStatus == ForestOnboardingDeliveryStatus.Synced) progress
                else progress.copy(
                    onboardingSyncStatus = ForestOnboardingDeliveryStatus.Pending,
                    onboardingNextRetryAtEpochMillis = null,
                    onboardingLastErrorCode = null,
                )
            }
            account.checkSession(authSession)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun observeOnboardingWork(): Flow<ForestOnboardingWork?> = combine(
        authSession.identity, authSession.state,
    ) { identity, state -> identity to state }.flatMapLatest { (identity, state) ->
        flow {
            if (identity == null || state != AuthSessionState.Authenticated) {
                emit(null)
                return@flow
            }
            val account = captureAlarmOccurrenceAccount(tokenStorage, authSession)
            if (account == null || account.sessionIdentity !== identity) {
                emit(null)
                return@flow
            }
            localProgressStore.observe(account.ownerAccountId).collect { progress ->
                emit(if (authSession.identity.value === identity &&
                    authSession.state.value == AuthSessionState.Authenticated) ForestOnboardingWork(account, progress) else null)
            }
        }
    }

    private suspend fun sessionDeliveryMutex(account: AlarmOccurrenceAccount): Mutex = snapshotMutex.withLock {
        account.checkSession(authSession)
        if (deliverySessionIdentity !== account.sessionIdentity) {
            deliverySessionIdentity = account.sessionIdentity
            deliveryMutex = Mutex()
        }
        deliveryMutex
    }

    /** Compatibility entry point: only delivers onboarding. */
    internal suspend fun syncOnboarding(now: () -> Long): Long? {
        val account = captureAlarmOccurrenceAccount(tokenStorage, authSession) ?: return null
        return sessionDeliveryMutex(account).withLock {
            account.checkSession(authSession)
            if (unauthorizedSessionIdentity === account.sessionIdentity) return@withLock null
            syncOnboardingLocked(account, now)
        }
    }

    /** One account-owned batch; network operations never hold snapshotMutex. */
    internal suspend fun syncForest(now: () -> Long): Long? {
        val account = captureAlarmOccurrenceAccount(tokenStorage, authSession) ?: return null
        return sessionDeliveryMutex(account).withLock {
            account.checkSession(authSession)
            if (unauthorizedSessionIdentity === account.sessionIdentity) return@withLock null
            val plantingDeadline = syncPlantingLocked(account, now)
            if (unauthorizedSessionIdentity === account.sessionIdentity) return@withLock plantingDeadline
            val onboardingDeadline = syncOnboardingLocked(account, now)
            val progress = readProgress(account)
            if (progress.onboardingSyncStatus != ForestOnboardingDeliveryStatus.Synced ||
                progress.onboardingCompletedAtEpochMillis == null || unauthorizedSessionIdentity === account.sessionIdentity) {
                return@withLock listOfNotNull(plantingDeadline, onboardingDeadline).minOrNull()
            }
            listOfNotNull(plantingDeadline, onboardingDeadline, syncTreesLocked(account, now)).minOrNull()
        }
    }

    override suspend fun recordConfirmedArrival(account: AlarmOccurrenceAccount, occurrenceId: String, arrivedAtEpochMillis: Long) {
        require(occurrenceId.isNotBlank() && arrivedAtEpochMillis >= 0)
        snapshotMutex.withLock {
            account.checkSession(authSession)
            localProgressStore.update(account.ownerAccountId) { progress ->
                account.checkSession(authSession)
                val existing = progress.arrivalPlantingCheckpoints[occurrenceId]
                require(existing == null || existing.arrivedAtEpochMillis == arrivedAtEpochMillis) {
                    "이미 확인한 도착 시각과 일치하지 않습니다."
                }
                if (existing != null) progress else progress.copy(
                    arrivalPlantingCheckpoints = progress.arrivalPlantingCheckpoints +
                        (occurrenceId to ForestArrivalPlantingCheckpoint(occurrenceId, arrivedAtEpochMillis)),
                )
            }
        }
    }

    override fun observeArrivalPlantingWork(): Flow<Unit> = observeOnboardingWork()
        .map { it?.progress?.arrivalPlantingCheckpoints }.distinctUntilChanged().map { }

    override suspend fun resolvePendingArrivals(account: AlarmOccurrenceAccount, now: () -> Long): ForestArrivalPlantingGate {
        // PUT delivery has a different mutex: a slow tree response must not delay the next arrival's GET.
        val mutex = snapshotMutex.withLock {
            account.checkSession(authSession)
            if (arrivalSessionIdentity !== account.sessionIdentity) {
                arrivalSessionIdentity = account.sessionIdentity
                arrivalMutex = Mutex()
            }
            arrivalMutex
        }
        return mutex.withLock {
            while (true) {
                account.checkSession(authSession)
                if (unauthorizedSessionIdentity === account.sessionIdentity) return@withLock ForestArrivalPlantingGate(false)
                val checkpoint = readProgress(account).arrivalPlantingCheckpoints.values
                    .filter { it.status != ForestArrivalPlantingStatus.Resolved }
                    .minWithOrNull(compareBy<ForestArrivalPlantingCheckpoint> { it.arrivedAtEpochMillis }.thenBy { it.occurrenceId })
                    ?: return@withLock ForestArrivalPlantingGate(true)
                if (checkpoint.status == ForestArrivalPlantingStatus.Blocked) return@withLock ForestArrivalPlantingGate(false)
                checkpoint.nextRetryAtEpochMillis?.takeIf { it > now() }?.let {
                    return@withLock ForestArrivalPlantingGate(false, it)
                }
                val claimed = checkpoint.copy(attemptCount = nextAttempt(checkpoint.attemptCount), nextRetryAtEpochMillis = null, lastErrorCode = null)
                val (readSequence, missingBeforeRequest) = snapshotMutex.withLock {
                    account.checkSession(authSession)
                    localProgressStore.update(account.ownerAccountId) { progress ->
                        account.checkSession(authSession)
                        if (progress.arrivalPlantingCheckpoints[checkpoint.occurrenceId] != checkpoint) progress
                        else progress.copy(arrivalPlantingCheckpoints = progress.arrivalPlantingCheckpoints + (checkpoint.occurrenceId to claimed))
                    }
                    ++remoteReadSequence to missingTreeDeliveries(localProgressStore.read(account.ownerAccountId))
                }
                if (readProgress(account).arrivalPlantingCheckpoints[checkpoint.occurrenceId] != claimed) continue
                evaluatingArrival.value = account
                var serverConfirmed = false
                try {
                    // Always starts after recordConfirmedArrival. A displayed/cached GET cannot prove this checkpoint.
                    val remote = fetchRemoteForest(account)
                    serverConfirmed = true
                    snapshotMutex.withLock {
                        account.checkSession(authSession)
                        localProgressStore.update(account.ownerAccountId) { progress ->
                            account.checkSession(authSession)
                            if (progress.arrivalPlantingCheckpoints[checkpoint.occurrenceId] != claimed) progress
                            else {
                                val days = remote.growth.successfulDayCount
                                val cycle = ForestPlantingPolicy.growthCycleToPlant(days)?.takeIf { candidate ->
                                    remote.trees.none { it.growthCycle == candidate } && candidate !in progress.treePlantingDeliveries
                                }
                                progress.copy(
                                    arrivalPlantingCheckpoints = progress.arrivalPlantingCheckpoints + (checkpoint.occurrenceId to claimed.copy(
                                        status = ForestArrivalPlantingStatus.Resolved, successfulDayCount = days,
                                    )),
                                    treePlantingDeliveries = if (cycle == null) progress.treePlantingDeliveries else progress.treePlantingDeliveries +
                                        (cycle to ForestTreePlantingDelivery(cycle, ForestPlantingPolicy.chooseKind().persistedValue, claimed.arrivedAtEpochMillis)),
                                )
                            }
                        }
                        publishFresh(account, remote, readSequence, missingBeforeRequest)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    account.checkSession(authSession)
                    if (serverConfirmed) throw error
                    val unauthorized = error is ApiException && error.statusCode == 401
                    val retryable = isTransientForestFailure(error)
                    val updated = snapshotMutex.withLock {
                        account.checkSession(authSession)
                        localProgressStore.update(account.ownerAccountId) { progress ->
                            account.checkSession(authSession)
                            if (progress.arrivalPlantingCheckpoints[checkpoint.occurrenceId] != claimed) progress
                            else progress.copy(arrivalPlantingCheckpoints = progress.arrivalPlantingCheckpoints + (checkpoint.occurrenceId to claimed.copy(
                                status = if (retryable || unauthorized) ForestArrivalPlantingStatus.Pending else ForestArrivalPlantingStatus.Blocked,
                                nextRetryAtEpochMillis = if (retryable) now() + onboardingRetryDelayMillis(claimed.attemptCount) else null,
                                lastErrorCode = forestFailureCode(error),
                            )))
                        }
                    }
                    if (unauthorized) unauthorizedSessionIdentity = account.sessionIdentity
                    val remaining = updated.arrivalPlantingCheckpoints[checkpoint.occurrenceId]
                    if (remaining?.status != ForestArrivalPlantingStatus.Resolved) {
                        return@withLock ForestArrivalPlantingGate(false, remaining?.nextRetryAtEpochMillis)
                    }
                } finally {
                    if (evaluatingArrival.value == account) evaluatingArrival.value = null
                }
            }
            @Suppress("UNREACHABLE_CODE")
            ForestArrivalPlantingGate(true)
        }
    }

    override fun observePlantingSyncState(): Flow<ForestPlantingSyncState> = combine(
        observeOnboardingWork(), sendingPlanting, evaluatingArrival,
    ) { work, sending, evaluating ->
        val checkpoints = work?.progress?.arrivalPlantingCheckpoints?.values.orEmpty()
        val deliveries = work?.progress?.treePlantingDeliveries?.values.orEmpty()
        when {
            work == null || work.account.sessionIdentity !== authSession.identity.value ||
                authSession.state.value != AuthSessionState.Authenticated -> ForestPlantingSyncState.Idle
            checkpoints.any { it.status == ForestArrivalPlantingStatus.Blocked } ||
                deliveries.any { it.status == ForestTreePlantingDeliveryStatus.Blocked } -> ForestPlantingSyncState.Blocked
            sending == work.account || evaluating == work.account -> ForestPlantingSyncState.Syncing
            checkpoints.isEmpty() && deliveries.isEmpty() -> ForestPlantingSyncState.Idle
            checkpoints.any { it.status == ForestArrivalPlantingStatus.Pending } ||
                deliveries.any { it.status == ForestTreePlantingDeliveryStatus.Pending } -> ForestPlantingSyncState.Pending
            else -> ForestPlantingSyncState.Synced
        }
    }.distinctUntilChanged()

    override suspend fun retryPlantingSync() {
        val account = currentAccount()
        val conflicts = snapshotMutex.withLock {
            account.checkSession(authSession)
            localProgressStore.update(account.ownerAccountId) { progress ->
                account.checkSession(authSession)
                progress.copy(
                    arrivalPlantingCheckpoints = progress.arrivalPlantingCheckpoints.mapValues { (_, checkpoint) ->
                        if (checkpoint.status == ForestArrivalPlantingStatus.Resolved) checkpoint else checkpoint.copy(
                            status = ForestArrivalPlantingStatus.Pending, nextRetryAtEpochMillis = null, lastErrorCode = null,
                        )
                    },
                    treePlantingDeliveries = progress.treePlantingDeliveries.mapValues { (_, delivery) ->
                        if (delivery.status == ForestTreePlantingDeliveryStatus.Synced || delivery.lastErrorCode == "FOREST409") delivery
                        else delivery.copy(status = ForestTreePlantingDeliveryStatus.Pending, nextRetryAtEpochMillis = null, lastErrorCode = null)
                    },
                )
            }.treePlantingDeliveries.values.any { it.status == ForestTreePlantingDeliveryStatus.Blocked && it.lastErrorCode == "FOREST409" }
        }
        if (!conflicts) return
        // A planting conflict is not an onboarding conflict. Only server existence can resolve it.
        val readSequence = snapshotMutex.withLock { account.checkSession(authSession); ++remoteReadSequence }
        val remote = fetchRemoteForest(account)
        snapshotMutex.withLock {
            account.checkSession(authSession)
            publishFresh(account, remote, readSequence)
        }
    }

    private suspend fun syncPlantingLocked(account: AlarmOccurrenceAccount, now: () -> Long): Long? {
        val cycles = readProgress(account).treePlantingDeliveries.keys.sorted()
        for (cycle in cycles) {
            account.checkSession(authSession)
            if (unauthorizedSessionIdentity === account.sessionIdentity) return null
            val candidate = readProgress(account).treePlantingDeliveries[cycle] ?: continue
            if (candidate.status != ForestTreePlantingDeliveryStatus.Pending || candidate.nextRetryAtEpochMillis?.let { it > now() } == true) continue
            val claimed = candidate.copy(attemptCount = nextAttempt(candidate.attemptCount), nextRetryAtEpochMillis = null, lastErrorCode = null)
            val progress = snapshotMutex.withLock {
                account.checkSession(authSession)
                localProgressStore.update(account.ownerAccountId) { current ->
                    account.checkSession(authSession)
                    if (current.treePlantingDeliveries[cycle] != candidate) current
                    else current.copy(treePlantingDeliveries = current.treePlantingDeliveries + (cycle to claimed))
                }
            }
            if (progress.treePlantingDeliveries[cycle] != claimed) continue
            sendingPlanting.value = account
            var serverConfirmed = false
            try {
                val tree = treeRemote.plant(account, claimed)
                snapshotMutex.withLock {
                    account.checkSession(authSession)
                    val cached = snapshot.value?.takeIf { it.account == account }
                    require(cached?.serverState?.trees.orEmpty().none { it.growthCycle != tree.growthCycle && it.slotIndex == tree.slotIndex }) {
                        "서버 나무의 칸이 다른 나무와 겹칩니다."
                    }
                    serverConfirmed = true
                    reconcileTrees(account, listOf(tree))
                    cached?.let { publish(account, it.serverState) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                account.checkSession(authSession)
                if (serverConfirmed) throw error
                val unauthorized = error is ApiException && error.statusCode == 401
                val retryable = isTransientForestFailure(error)
                snapshotMutex.withLock {
                    account.checkSession(authSession)
                    localProgressStore.update(account.ownerAccountId) { current ->
                        account.checkSession(authSession)
                        if (current.treePlantingDeliveries[cycle] != claimed) current
                        else current.copy(treePlantingDeliveries = current.treePlantingDeliveries + (cycle to claimed.copy(
                            status = if (retryable || unauthorized) ForestTreePlantingDeliveryStatus.Pending else ForestTreePlantingDeliveryStatus.Blocked,
                            nextRetryAtEpochMillis = if (retryable) now() + onboardingRetryDelayMillis(claimed.attemptCount) else null,
                            lastErrorCode = if (error is ApiException && error.statusCode == 409) "FOREST409" else forestFailureCode(error),
                        )))
                    }
                }
                if (unauthorized) unauthorizedSessionIdentity = account.sessionIdentity
            } finally {
                if (sendingPlanting.value == account) sendingPlanting.value = null
            }
        }
        return readProgress(account).treePlantingDeliveries.values.filter { it.status == ForestTreePlantingDeliveryStatus.Pending }
            .minOfOrNull { it.nextRetryAtEpochMillis ?: now() }
    }

    private suspend fun syncOnboardingLocked(account: AlarmOccurrenceAccount, now: () -> Long): Long? {
        val progress = snapshotMutex.withLock {
            account.checkSession(authSession)
            localProgressStore.read(account.ownerAccountId)
        }
        val completedAt = progress.onboardingCompletedAtEpochMillis ?: return null
        if (progress.onboardingSyncStatus != ForestOnboardingDeliveryStatus.Pending) return null
        progress.onboardingNextRetryAtEpochMillis?.takeIf { it > now() }?.let { return it }
        val attempt = (progress.onboardingAttemptCount.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val claimed = snapshotMutex.withLock {
            account.checkSession(authSession)
            localProgressStore.update(account.ownerAccountId) { current ->
                account.checkSession(authSession)
                if (current.onboardingSyncStatus == ForestOnboardingDeliveryStatus.Pending &&
                    current.onboardingCompletedAtEpochMillis == completedAt) current.copy(onboardingAttemptCount = attempt)
                else current
            }
        }
        if (claimed.onboardingSyncStatus != ForestOnboardingDeliveryStatus.Pending ||
            claimed.onboardingCompletedAtEpochMillis != completedAt) return null
        account.checkSession(authSession)
        sendingOnboarding.value = account
        var serverConfirmed = false
        return try {
            val serverTime = onboardingRemote.complete(account, completedAt)
            serverConfirmed = true
            currentCoroutineContext().ensureActive()
            snapshotMutex.withLock {
                account.checkSession(authSession)
                val confirmed = confirmOnboarding(account, serverTime)
                snapshot.value?.takeIf { it.account == account }?.let { cached ->
                    publish(account, cached.serverState.copy(onboardingCompletedAtEpochMillis = confirmed.onboardingCompletedAtEpochMillis))
                }
            }
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            account.checkSession(authSession)
            // A local persistence failure after a valid response is not an invalid API response.
            if (serverConfirmed) throw error
            val unauthorized = error is ApiException && error.statusCode == 401
            val retryable = when (error) {
                is ApiException -> error.statusCode in setOf(408, 429) || error.statusCode in 500..599
                is IOException, is HttpRequestTimeoutException -> true
                else -> false
            }
            val retryAt = if (retryable) now() + onboardingRetryDelayMillis(attempt) else null
            val result = snapshotMutex.withLock {
                account.checkSession(authSession)
                localProgressStore.update(account.ownerAccountId) { current ->
                    account.checkSession(authSession)
                    if (current.onboardingSyncStatus == ForestOnboardingDeliveryStatus.Synced ||
                        current.onboardingCompletedAtEpochMillis != completedAt || current.onboardingAttemptCount != attempt) current
                    else current.copy(
                        onboardingSyncStatus = if (retryable || unauthorized) ForestOnboardingDeliveryStatus.Pending else ForestOnboardingDeliveryStatus.Blocked,
                        onboardingNextRetryAtEpochMillis = retryAt,
                        onboardingLastErrorCode = if (unauthorized) "AUTH_REQUIRED" else (error as? ApiException)?.code
                            ?: if (retryable) "NETWORK_ERROR" else "INVALID_RESPONSE",
                    )
                }
            }
            if (unauthorized) unauthorizedSessionIdentity = account.sessionIdentity
            if (result.onboardingSyncStatus == ForestOnboardingDeliveryStatus.Pending) result.onboardingNextRetryAtEpochMillis else null
        } finally {
            if (sendingOnboarding.value == account) sendingOnboarding.value = null
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

    override fun observeTreeAcknowledgementSyncState(): Flow<ForestTreeAcknowledgementSyncState> = combine(
        observeOnboardingWork(), sendingTree,
    ) { work, sending ->
        val deliveries = work?.progress?.treeAcknowledgementDeliveries?.values.orEmpty()
        when {
            work == null || work.account.sessionIdentity !== authSession.identity.value ||
                authSession.state.value != AuthSessionState.Authenticated || deliveries.isEmpty() -> ForestTreeAcknowledgementSyncState.Idle
            deliveries.any { it.status == ForestTreeAcknowledgementDeliveryStatus.Blocked } -> ForestTreeAcknowledgementSyncState.Blocked
            sending == work.account -> ForestTreeAcknowledgementSyncState.Syncing
            deliveries.all { it.status == ForestTreeAcknowledgementDeliveryStatus.Synced } -> ForestTreeAcknowledgementSyncState.Synced
            else -> ForestTreeAcknowledgementSyncState.Pending
        }
    }.distinctUntilChanged()

    override suspend fun retryTreeAcknowledgementSync() {
        val account = currentAccount()
        val missing = snapshotMutex.withLock {
            account.checkSession(authSession)
            val updated = localProgressStore.update(account.ownerAccountId) { progress ->
                account.checkSession(authSession)
                progress.copy(treeAcknowledgementDeliveries = progress.treeAcknowledgementDeliveries.mapValues { (_, value) ->
                    if (value.status == ForestTreeAcknowledgementDeliveryStatus.Synced || value.isMissingTree()) value
                    else value.copy(
                        status = ForestTreeAcknowledgementDeliveryStatus.Pending,
                        nextRetryAtEpochMillis = null,
                        lastErrorCode = null,
                        onboardingRepairCompleted = false,
                    )
                })
            }
            missingTreeDeliveries(updated)
        }
        if (missing.isEmpty()) return
        // This validation GET must not supersede or cancel the visible screen's GET.
        val remote = fetchRemoteForest(account)
        snapshotMutex.withLock {
            account.checkSession(authSession)
            reconcileTrees(account, remote.trees, missing, manualRetry = true)
            snapshot.value?.takeIf { it.account == account }?.let { publish(account, it.serverState) }
        }
    }

    private suspend fun readProgress(account: AlarmOccurrenceAccount): ForestLocalProgress = snapshotMutex.withLock {
        account.checkSession(authSession)
        localProgressStore.read(account.ownerAccountId).also { account.checkSession(authSession) }
    }

    private suspend fun syncTreesLocked(account: AlarmOccurrenceAccount, now: () -> Long): Long? {
        val keys = readProgress(account).treeAcknowledgementDeliveries.values.sortedBy { it.growthCycle }
            .map { it.localProgressKey() }
        for (key in keys) {
            // At most PATCH -> repair PUT -> PATCH. Repeated conflicts cannot create a busy loop.
            repeat(3) {
                account.checkSession(authSession)
                if (unauthorizedSessionIdentity === account.sessionIdentity) return null
                val candidate = readProgress(account).treeAcknowledgementDeliveries[key] ?: return@repeat
                if (!candidate.isDeliverable() || candidate.nextRetryAtEpochMillis?.let { it > now() } == true) return@repeat
                val claimed = claimTree(account, key, candidate) ?: return@repeat
                sendingTree.value = account
                var serverConfirmed = false
                try {
                    if (claimed.status == ForestTreeAcknowledgementDeliveryStatus.NeedsOnboarding) {
                        val completedAt = checkNotNull(readProgress(account).onboardingCompletedAtEpochMillis) {
                            "온보딩 완료 시각이 없습니다."
                        }
                        val serverTime = onboardingRemote.complete(account, completedAt)
                        serverConfirmed = true
                        finishOnboardingRepair(account, key, claimed, serverTime)
                    } else {
                        val tree = treeRemote.acknowledge(account, claimed)
                        serverConfirmed = true
                        snapshotMutex.withLock {
                            account.checkSession(authSession)
                            val confirmed = reconcileTrees(account, listOf(tree))
                            val canonical = tree.copy(acknowledgedAtEpochMillis = confirmed.acknowledgedTrees[key])
                            snapshot.value?.takeIf { it.account == account }?.let { cached ->
                                publish(account, cached.serverState.copy(trees = cached.serverState.trees.map {
                                    if (claimed.matches(it)) canonical else it
                                }))
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    account.checkSession(authSession)
                    // Preserve the pending record if disk persistence failed after server success.
                    if (serverConfirmed) throw error
                    failTree(account, key, claimed, error, now())
                } finally {
                    if (sendingTree.value == account) sendingTree.value = null
                }
            }
        }
        return readProgress(account).treeAcknowledgementDeliveries.values.filter { it.isDeliverable() }
            .minOfOrNull { it.nextRetryAtEpochMillis ?: now() }
    }

    private suspend fun claimTree(
        account: AlarmOccurrenceAccount,
        key: String,
        candidate: ForestTreeAcknowledgementDelivery,
    ): ForestTreeAcknowledgementDelivery? = snapshotMutex.withLock {
        account.checkSession(authSession)
        val claimed = candidate.copy(
            attemptCount = (candidate.attemptCount.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            nextRetryAtEpochMillis = null,
            lastErrorCode = null,
        )
        val progress = localProgressStore.update(account.ownerAccountId) { current ->
            account.checkSession(authSession)
            if (current.treeAcknowledgementDeliveries[key] != candidate) current
            else current.copy(treeAcknowledgementDeliveries = current.treeAcknowledgementDeliveries + (key to claimed))
        }
        account.checkSession(authSession)
        claimed.takeIf { progress.treeAcknowledgementDeliveries[key] == it }
    }

    private suspend fun finishOnboardingRepair(
        account: AlarmOccurrenceAccount,
        key: String,
        claimed: ForestTreeAcknowledgementDelivery,
        serverTime: Long,
    ) = snapshotMutex.withLock {
        account.checkSession(authSession)
        localProgressStore.update(account.ownerAccountId) { progress ->
            account.checkSession(authSession)
            val current = progress.treeAcknowledgementDeliveries[key]
            // An explicit 409 repair learns the current server value, even after an older Synced value.
            progress.copy(
                onboardingCompletedAtEpochMillis = serverTime,
                onboardingSyncStatus = ForestOnboardingDeliveryStatus.Synced,
                onboardingAttemptCount = 0,
                onboardingNextRetryAtEpochMillis = null,
                onboardingLastErrorCode = null,
                treeAcknowledgementDeliveries = if (current != claimed) progress.treeAcknowledgementDeliveries
                else progress.treeAcknowledgementDeliveries + (key to claimed.copy(
                    status = ForestTreeAcknowledgementDeliveryStatus.Pending,
                    onboardingRepairCompleted = true,
                    nextRetryAtEpochMillis = null,
                    lastErrorCode = null,
                )),
            )
        }
        snapshot.value?.takeIf { it.account == account }?.let { cached ->
            publish(account, cached.serverState.copy(onboardingCompletedAtEpochMillis = serverTime))
        }
    }

    private suspend fun failTree(
        account: AlarmOccurrenceAccount,
        key: String,
        claimed: ForestTreeAcknowledgementDelivery,
        error: Exception,
        failedAt: Long,
    ) {
        val status = (error as? ApiException)?.statusCode
        val unauthorized = status == 401
        val transient = when (error) {
            is ApiException -> error.statusCode in setOf(408, 429) || error.statusCode in 500..599
            is IOException, is HttpRequestTimeoutException -> true
            else -> false
        }
        val conflict = status == 409 && claimed.status == ForestTreeAcknowledgementDeliveryStatus.Pending
        val missing = status == 404 && claimed.status == ForestTreeAcknowledgementDeliveryStatus.Pending
        val nextStatus = when {
            conflict && !claimed.onboardingRepairCompleted -> ForestTreeAcknowledgementDeliveryStatus.NeedsOnboarding
            transient || unauthorized -> claimed.status
            else -> ForestTreeAcknowledgementDeliveryStatus.Blocked
        }
        val errorCode = when {
            unauthorized -> "AUTH_REQUIRED"
            conflict -> "FOREST409"
            missing -> "FOREST404"
            claimed.status == ForestTreeAcknowledgementDeliveryStatus.NeedsOnboarding && status != null -> "ONBOARDING_$status"
            else -> (error as? ApiException)?.code ?: if (transient) "NETWORK_ERROR" else "INVALID_RESPONSE"
        }
        snapshotMutex.withLock {
            account.checkSession(authSession)
            localProgressStore.update(account.ownerAccountId) { progress ->
                account.checkSession(authSession)
                if (progress.treeAcknowledgementDeliveries[key] != claimed) progress
                else progress.copy(treeAcknowledgementDeliveries = progress.treeAcknowledgementDeliveries + (key to claimed.copy(
                    status = nextStatus,
                    nextRetryAtEpochMillis = if (transient || missing) failedAt + onboardingRetryDelayMillis(claimed.attemptCount) else null,
                    lastErrorCode = errorCode,
                )))
            }
        }
        if (unauthorized) unauthorizedSessionIdentity = account.sessionIdentity
    }

    /** Caller holds snapshotMutex; only a new GET may release a previously captured 404 record. */
    private suspend fun reconcileTrees(
        account: AlarmOccurrenceAccount,
        trees: List<ForestTree>,
        missingBeforeRequest: Map<String, ForestTreeAcknowledgementDelivery> = emptyMap(),
        manualRetry: Boolean = false,
    ): ForestLocalProgress = localProgressStore.update(account.ownerAccountId) { progress ->
        account.checkSession(authSession)
        val acknowledgements = progress.acknowledgedTrees.toMutableMap()
        val deliveries = progress.treeAcknowledgementDeliveries.toMutableMap()
        val plantings = progress.treePlantingDeliveries.toMutableMap()
        for (tree in trees) {
            plantings[tree.growthCycle]?.let { planting ->
                val previous = planting.confirmedTree?.toDomain()
                val canonical = previous?.copy(acknowledgedAtEpochMillis = previous.acknowledgedAtEpochMillis ?:
                    tree.acknowledgedAtEpochMillis.takeIf { previous.localProgressKey() == tree.localProgressKey() }) ?: tree
                plantings[tree.growthCycle] = planting.copy(
                    status = ForestTreePlantingDeliveryStatus.Synced,
                    nextRetryAtEpochMillis = null,
                    lastErrorCode = null,
                    confirmedTree = canonical.toStoredTree(),
                )
            }
            val key = tree.localProgressKey()
            val current = deliveries[key]
            val serverTime = tree.acknowledgedAtEpochMillis
            if (serverTime != null) {
                val canonical = current?.takeIf { it.status == ForestTreeAcknowledgementDeliveryStatus.Synced }
                    ?.acknowledgedAtEpochMillis ?: serverTime
                acknowledgements[key] = canonical
                if (current != null) deliveries[key] = current.copy(
                    acknowledgedAtEpochMillis = canonical,
                    status = ForestTreeAcknowledgementDeliveryStatus.Synced,
                    nextRetryAtEpochMillis = null,
                    lastErrorCode = null,
                )
            } else if (current != null && current.isMissingTree() && missingBeforeRequest[key] == current) {
                deliveries[key] = current.copy(
                    status = ForestTreeAcknowledgementDeliveryStatus.Pending,
                    nextRetryAtEpochMillis = if (manualRetry) null else current.nextRetryAtEpochMillis,
                    lastErrorCode = null,
                    onboardingRepairCompleted = if (manualRetry) false else current.onboardingRepairCompleted,
                )
            }
        }
        progress.copy(acknowledgedTrees = acknowledgements, treeAcknowledgementDeliveries = deliveries, treePlantingDeliveries = plantings)
    }

    private suspend fun currentAccount(): AlarmOccurrenceAccount =
        checkNotNull(captureAlarmOccurrenceAccount(tokenStorage, authSession)) { "로그인이 필요한 기능이에요." }

    private fun currentSnapshot(account: AlarmOccurrenceAccount): CachedForest {
        account.checkSession(authSession)
        return checkNotNull(snapshot.value?.takeIf { it.account == account }) { "숲 기록을 먼저 불러와 주세요." }
    }

    /** GET publication order is independent of the UI refresh cancellation sequence. */
    private suspend fun publishFresh(
        account: AlarmOccurrenceAccount,
        serverState: ForestState,
        readSequence: Long,
        missingBeforeRequest: Map<String, ForestTreeAcknowledgementDelivery> = emptyMap(),
    ): ForestState {
        account.checkSession(authSession)
        val latest = snapshot.value?.takeIf { it.account == account }
        if (latest != null && readSequence < publishedReadSequence) {
            serverState.onboardingCompletedAtEpochMillis?.let { confirmOnboarding(account, it) }
            reconcileTrees(account, serverState.trees, missingBeforeRequest)
            return publish(account, latest.serverState)
        }
        return publish(account, serverState, missingBeforeRequest).also { publishedReadSequence = readSequence }
    }

    /** Called under snapshotMutex so a completed UI action cannot be rolled back by a refresh. */
    private suspend fun publish(
        account: AlarmOccurrenceAccount,
        serverState: ForestState,
        missingBeforeRequest: Map<String, ForestTreeAcknowledgementDelivery> = emptyMap(),
    ): ForestState {
        account.checkSession(authSession)
        serverState.onboardingCompletedAtEpochMillis?.let { confirmOnboarding(account, it) }
        val progress = reconcileTrees(account, serverState.trees, missingBeforeRequest)
        val confirmedTime = progress.onboardingCompletedAtEpochMillis.takeIf {
            progress.onboardingSyncStatus == ForestOnboardingDeliveryStatus.Synced
        }
        val trees = serverState.trees.associateBy { it.growthCycle }.toMutableMap()
        for (planting in progress.treePlantingDeliveries.values) {
            if (planting.status == ForestTreePlantingDeliveryStatus.Synced) {
                planting.confirmedTree?.toDomain()?.let { trees[it.growthCycle] = it }
            }
        }
        require(trees.values.map { it.slotIndex }.distinct().size == trees.size) { "서버 나무의 칸이 겹칩니다." }
        val confirmedServerState = serverState.copy(
            onboardingCompletedAtEpochMillis = confirmedTime ?: serverState.onboardingCompletedAtEpochMillis,
            trees = trees.values.sortedBy { it.growthCycle }.map { tree ->
                val delivery = progress.treeAcknowledgementDeliveries[tree.localProgressKey()]
                if (delivery?.status == ForestTreeAcknowledgementDeliveryStatus.Synced) {
                    tree.copy(acknowledgedAtEpochMillis = delivery.acknowledgedAtEpochMillis)
                } else tree
            },
        )
        val state = confirmedServerState.withLocalProgress(progress)
        currentCoroutineContext().ensureActive()
        account.checkSession(authSession)
        snapshot.value = CachedForest(account, confirmedServerState, state)
        return state
    }

    /** Caller holds snapshotMutex; the server's immutable first completion wins once confirmed. */
    private suspend fun confirmOnboarding(account: AlarmOccurrenceAccount, serverTime: Long): ForestLocalProgress =
        localProgressStore.update(account.ownerAccountId) { progress ->
            account.checkSession(authSession)
            if (progress.onboardingSyncStatus == ForestOnboardingDeliveryStatus.Synced && progress.onboardingCompletedAtEpochMillis != null) progress
            else progress.copy(
                onboardingCompletedAtEpochMillis = serverTime,
                onboardingSyncStatus = ForestOnboardingDeliveryStatus.Synced,
                onboardingAttemptCount = 0,
                onboardingNextRetryAtEpochMillis = null,
                onboardingLastErrorCode = null,
            )
        }
}

internal data class ForestOnboardingWork(val account: AlarmOccurrenceAccount, val progress: ForestLocalProgress)

internal fun onboardingRetryDelayMillis(attemptCount: Int): Long {
    require(attemptCount > 0)
    return (5_000L * (1L shl (attemptCount - 1).coerceAtMost(8))).coerceAtMost(900_000L)
}

private data class CachedForest(val account: AlarmOccurrenceAccount, val serverState: ForestState, val state: ForestState)

private fun nextAttempt(attempt: Int): Int = (attempt.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private fun isTransientForestFailure(error: Exception): Boolean = when (error) {
    is ApiException -> error.statusCode in setOf(408, 429) || error.statusCode in 500..599
    is IOException, is HttpRequestTimeoutException -> true
    else -> false
}

private fun forestFailureCode(error: Exception): String =
    if (error is ApiException && error.statusCode == 401) "AUTH_REQUIRED"
    else (error as? ApiException)?.code ?: if (isTransientForestFailure(error)) "NETWORK_ERROR" else "INVALID_RESPONSE"

private fun ForestTreeAcknowledgementDelivery.isDeliverable(): Boolean =
    status == ForestTreeAcknowledgementDeliveryStatus.Pending || status == ForestTreeAcknowledgementDeliveryStatus.NeedsOnboarding

private fun ForestTreeAcknowledgementDelivery.isMissingTree(): Boolean =
    status == ForestTreeAcknowledgementDeliveryStatus.Blocked && lastErrorCode == "FOREST404"

private fun missingTreeDeliveries(progress: ForestLocalProgress): Map<String, ForestTreeAcknowledgementDelivery> =
    progress.treeAcknowledgementDeliveries.filterValues { it.isMissingTree() }

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
