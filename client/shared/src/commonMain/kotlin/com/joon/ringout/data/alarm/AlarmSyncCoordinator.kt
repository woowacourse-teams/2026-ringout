package com.joon.ringout.data.alarm

import com.joon.ringout.data.destination.DestinationAccountProvider
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

internal class AlarmSyncCoordinator(
    private val syncer: AlarmOutboxSyncer,
    private val accountProvider: DestinationAccountProvider,
    private val authSession: AuthSession,
    private val networkMonitor: NetworkMonitor,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val onStorageFailure: (Exception) -> Unit = {},
) {
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    suspend fun prepareCurrentSession(forceRestore: Boolean) {
        syncer.prepareCurrentSession(forceRestore)
        requestSync()
    }

    fun requestSync() {
        wakeups.trySend(Unit)
    }

    fun start(scope: CoroutineScope): Job = scope.launch {
        launch { syncer.outbox.collect { requestSync() } }
        launch { accountProvider.accountIds.collect { requestSync() } }
        launch { authSession.identity.collect { requestSync() } }
        launch { authSession.state.collect { requestSync() } }
        launch { networkMonitor.status.collect { requestSync() } }

        var nextRetryAt: Long? = null
        var storageRetryAt: Long? = null
        while (true) {
            val waitMillis = nextRetryAt?.let { (it - now()).coerceAtLeast(1) }
            if (waitMillis == null) {
                wakeups.receive()
            } else {
                withTimeoutOrNull(waitMillis) { wakeups.receive() }
            }
            if (networkMonitor.status.value != NetworkStatus.Online) {
                nextRetryAt = null
                continue
            }
            if (storageRetryAt?.let { it > now() } == true) {
                nextRetryAt = storageRetryAt
                continue
            }
            try {
                nextRetryAt = syncer.flush()
                storageRetryAt = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onStorageFailure(error)
                storageRetryAt = now() + StorageRetryDelayMillis
                nextRetryAt = storageRetryAt
            }
        }
    }

    private companion object {
        const val StorageRetryDelayMillis = 5_000L
    }
}
