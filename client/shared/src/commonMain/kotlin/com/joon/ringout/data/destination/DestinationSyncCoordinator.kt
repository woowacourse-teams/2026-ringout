package com.joon.ringout.data.destination

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/** Wakes destination restore and outbox delivery on account, network, database, and retry changes. */
internal class DestinationSyncCoordinator(
    private val syncer: DestinationOutboxSyncer,
    private val dao: SavedDestinationDao,
    private val accountProvider: DestinationAccountProvider,
    private val authSession: AuthSession,
    private val networkMonitor: NetworkMonitor,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val onStorageFailure: (Exception) -> Unit = {},
) {
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    fun requestSync() {
        wakeups.trySend(Unit)
    }

    fun start(scope: CoroutineScope): Job = scope.launch {
        val network = MutableStateFlow(NetworkStatus.Unknown)
        val observers = listOf(
            launch { dao.observeOutbox().collect { wakeups.trySend(Unit) } },
            launch { dao.observeSyncStates().collect { wakeups.trySend(Unit) } },
            launch { accountProvider.accountIds.collect { wakeups.trySend(Unit) } },
            launch { authSession.identity.collect { wakeups.trySend(Unit) } },
            launch { authSession.state.collect { wakeups.trySend(Unit) } },
            launch { networkMonitor.status.collect { network.value = it; wakeups.trySend(Unit) } },
        )
        var nextRetryAt: Long? = null
        var storageRetryAt: Long? = null
        try {
            while (true) {
                val waitMillis = nextRetryAt?.let { (it - now()).coerceAtLeast(1) }
                if (waitMillis == null) wakeups.receive() else withTimeoutOrNull(waitMillis) { wakeups.receive() }
                if (storageRetryAt?.let { it > now() } == true) {
                    nextRetryAt = storageRetryAt
                    continue
                }
                try {
                    syncer.prepareAccountForCurrentSession()
                    if (network.value != NetworkStatus.Online) {
                        nextRetryAt = null
                        continue
                    }
                    nextRetryAt = syncer.flush().also { storageRetryAt = null }
                } catch (_: DestinationAccountChanged) {
                    nextRetryAt = null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    onStorageFailure(error)
                    nextRetryAt = (now() + 5_000L).also { storageRetryAt = it }
                }
            }
        } finally {
            observers.forEach { it.cancel() }
            wakeups.close()
        }
    }
}
