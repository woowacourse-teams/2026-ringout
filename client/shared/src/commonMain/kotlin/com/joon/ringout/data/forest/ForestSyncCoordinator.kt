package com.joon.ringout.data.forest

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/** Process-lifetime delivery; screen disposal never owns or cancels a persisted completion. */
internal class ForestSyncCoordinator(
    private val repository: DefaultForestRepository,
    private val authSession: AuthSession,
    private val networkMonitor: NetworkMonitor,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    fun requestSync() { wakeups.trySend(Unit) }

    fun start(scope: CoroutineScope): Job {
        job?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            var sessionJob: Job? = null
            try {
                combine(authSession.identity, authSession.state) { identity, state -> identity to state }
                    .distinctUntilChanged().collect { (identity, state) ->
                        // Do not join an obsolete network response before allowing the new account to sync.
                        sessionJob?.cancel()
                        sessionJob = if (identity != null && state == AuthSessionState.Authenticated) {
                            launch { runSession(identity) }
                        } else null
                    }
            } finally {
                sessionJob?.cancel()
            }
        }.also { job = it }
    }

    private suspend fun runSession(identity: Any) = coroutineScope {
        val observers = listOf(
            launch {
                while (true) {
                    try {
                        repository.observeOnboardingWork().collect { requestSync() }
                        return@launch
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        requestSync()
                        delay(5_000)
                    }
                }
            },
            launch { networkMonitor.status.collect { requestSync() } },
        )
        var nextRetryAt: Long? = null
        var storageRetryAt: Long? = null
        try {
            requestSync()
            while (true) {
                val wait = nextRetryAt?.let { (it - now()).coerceAtLeast(1) }
                if (wait == null) wakeups.receive() else withTimeoutOrNull(wait) { wakeups.receive() }
                currentCoroutineContext().ensureActive()
                if (authSession.identity.value !== identity || authSession.state.value != AuthSessionState.Authenticated) return@coroutineScope
                if (storageRetryAt?.let { it > now() } == true) {
                    nextRetryAt = storageRetryAt
                    continue
                }
                if (networkMonitor.status.value != NetworkStatus.Online) {
                    nextRetryAt = null
                    continue
                }
                try {
                    nextRetryAt = repository.syncForest(now)
                    storageRetryAt = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    nextRetryAt = (now() + 5_000).also { storageRetryAt = it }
                }
            }
        } finally {
            observers.forEach { it.cancel() }
        }
    }
}

/** Source compatibility for onboarding-only callers and its regression tests. */
internal typealias ForestOnboardingSyncCoordinator = ForestSyncCoordinator
