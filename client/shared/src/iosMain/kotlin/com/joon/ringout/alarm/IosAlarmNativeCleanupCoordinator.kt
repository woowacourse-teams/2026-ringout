package com.joon.ringout.alarm

import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncDao
import com.joon.ringout.data.alarmoccurrence.IosAlarmCleanupReadinessReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSLog
import kotlin.time.Clock

/** Serializes proof-first cleanup requests and retries a candidate at its dedup-window boundary. */
internal class IosAlarmNativeCleanupCoordinator(
    private val inbox: IosAlarmMissionEventInbox,
    private val occurrenceDao: AlarmOccurrenceSyncDao,
    private val missionCoordinator: IosAlarmMissionCoordinator,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    startWorker: Boolean = true,
) {
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ ->
        NSLog("AlarmCleanup: retry on next app activation")
    })
    private var retryJob: Job? = null

    init {
        if (startWorker) {
            scope.launch {
                for (ignored in wakeups) {
                    val summary = try {
                        cleanupNow()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        NSLog("AlarmCleanup: candidate pass failed; native data preserved")
                        IosAlarmNativeCleanupSummary()
                    }
                    scheduleRetry(summary.retryAtEpochMillis)
                }
            }
        }
    }

    fun requestCleanup() {
        wakeups.trySend(Unit)
    }

    internal suspend fun cleanupNow(): IosAlarmNativeCleanupSummary {
        val candidates = inbox.nativeCleanupCandidatesAwait()
        var eventCommits = 0
        var chainCommits = 0
        var retryAt: Long? = null
        var preserved = 0

        for (candidate in candidates.events) {
            if (candidate.consumedAtEpochMillis == null) {
                preserved++
                continue
            }
            val dedupWindowEnd = candidate.occurredAtEpochMillis + DedupWindowMillis
            if (now() < dedupWindowEnd) {
                retryAt = retryAt?.let { minOf(it, dedupWindowEnd) } ?: dedupWindowEnd
                preserved++
                continue
            }
            if (candidate.requiresRecordProjection && candidate.recordProjectionAckAtEpochMillis == null) {
                preserved++
                continue
            }
            if (candidate.requiresRecordProjection) {
                val request = candidate.roomReadinessRequest()
                if (request == null || !isRoomReady(request)) {
                    preserved++
                    continue
                }
            }
            try {
                if (inbox.commitEventCleanupAwait(candidate.eventId, candidate.fingerprint).committed) {
                    eventCommits++
                } else {
                    preserved++
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                preserved++
            }
        }

        for (candidate in candidates.chains) {
            val request = candidate.roomReadinessRequest()
            if (request == null || !isRoomReady(request)) {
                preserved++
                continue
            }
            val references = candidate.protectedReferences
            try {
                val committed = missionCoordinator.compactIfUnprotected(references) {
                    inbox.compactTerminalChainAwait(candidate.chainId, candidate.fingerprint).committed
                }
                if (committed) chainCommits++ else preserved++
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                preserved++
            }
        }

        return IosAlarmNativeCleanupSummary(
            removedEvents = eventCommits,
            compactedChains = chainCommits,
            preservedCandidates = preserved,
            retryAtEpochMillis = retryAt,
        )
    }

    private suspend fun isRoomReady(
        request: com.joon.ringout.data.alarmoccurrence.IosAlarmCleanupReadinessRequest,
    ): Boolean = try {
        occurrenceDao.evaluateIosAlarmCleanup(request).reason == IosAlarmCleanupReadinessReason.READY
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    }

    private fun scheduleRetry(atEpochMillis: Long?) {
        retryJob?.cancel()
        if (atEpochMillis == null) return
        retryJob = scope.launch {
            delay((atEpochMillis - now()).coerceAtLeast(1L))
            wakeups.trySend(Unit)
        }
    }

    private companion object {
        const val DedupWindowMillis = 10_000L
    }
}

internal data class IosAlarmNativeCleanupSummary(
    val removedEvents: Int = 0,
    val compactedChains: Int = 0,
    val preservedCandidates: Int = 0,
    val retryAtEpochMillis: Long? = null,
)
