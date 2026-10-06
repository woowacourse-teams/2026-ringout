package com.joon.ringout.data.alarmoccurrence

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

/** 앱이 실행 가능한 동안의 재시도 루프. OS 백그라운드 스케줄러와 알람 발생 연결은 플랫폼에서 담당한다. */
internal class AlarmOccurrenceSyncCoordinator(
    private val syncer: AlarmOccurrenceSyncer,
    private val dao: AlarmOccurrenceSyncDao,
    private val authSession: AuthSession,
    private val networkMonitor: NetworkMonitor,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val onStorageFailure: (Exception) -> Unit = {},
    private val flush: suspend () -> Long? = syncer::flush,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        val wakeups = Channel<Unit>(Channel.CONFLATED)
        val network = MutableStateFlow(NetworkStatus.Unknown)
        val observers = listOf(
            launch { dao.observeUnsentEvents().collect { wakeups.trySend(Unit) } },
            launch { dao.observeUnsentMovements().collect { wakeups.trySend(Unit) } },
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
                if (network.value != NetworkStatus.Online) {
                    nextRetryAt = null
                    continue
                }
                if (storageRetryAt?.let { it > now() } == true) {
                    nextRetryAt = storageRetryAt
                    continue
                }
                nextRetryAt = try {
                    flush().also { storageRetryAt = null }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    onStorageFailure(error)
                    (now() + 5_000).also { storageRetryAt = it }
                }
            }
        } finally {
            observers.forEach { it.cancel() }
            wakeups.close()
        }
    }
}
