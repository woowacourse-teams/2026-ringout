package com.joon.ringout.data.alarm

import com.joon.ringout.data.destination.DestinationAccountChanged
import com.joon.ringout.data.destination.DestinationAccountContext
import com.joon.ringout.data.destination.DestinationAccountProvider
import com.joon.ringout.data.destination.DestinationRetryPolicy
import com.joon.ringout.data.destination.DestinationSyncSessionGate
import com.joon.ringout.data.network.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Clock

internal class AlarmOutboxSyncer(
    private val store: AlarmSyncStore,
    private val remoteDataSource: AlarmRemoteDataSource,
    private val accountProvider: DestinationAccountProvider,
    private val sessionGate: DestinationSyncSessionGate,
    private val applyServerChange: suspend (String, suspend () -> Boolean) -> Boolean,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val outbox = store.outbox

    private val preparedSession = MutableStateFlow<PreparedSession?>(null)
    private val flushMutex = Mutex()
    private var unauthorizedSession: Any? = null

    /** Called once ownership migration has finished for a verified account. */
    suspend fun prepareCurrentSession(forceRestore: Boolean) {
        val account = accountProvider.currentContext() ?: return
        try {
            sessionGate.withSession(account.sessionIdentity) {
                checkAccount(account)
                if (forceRestore) store.resetRestore(account.ownerAccountId)
                checkAccount(account)
                preparedSession.value = PreparedSession(account)
            }
        } catch (_: DestinationAccountChanged) {
            // A superseded bootstrap must not enable sync for the next session.
        }
    }

    suspend fun flush(): Long? = flushMutex.withLock {
        val session = preparedSession.value ?: return@withLock null
        if (unauthorizedSession === session.account.sessionIdentity) return@withLock null
        try {
            checkSession(session)
            if (!restoreIfNeeded(session)) return@withLock session.restoreRetryAt
            val outboxRetryAt = flushOutbox(session)
            listOfNotNull(session.restoreRetryAt, outboxRetryAt).minOrNull()
        } catch (_: DestinationAccountChanged) {
            null
        }
    }

    /**
     * A successful GET can leave native work deferred while another alarm is ringing.
     * In that case outbox delivery can proceed, and the next restore starts with a fresh GET.
     */
    private suspend fun restoreIfNeeded(session: PreparedSession): Boolean {
        val owner = session.account.ownerAccountId
        if (store.restoreComplete(owner)) return true
        if (session.restoreRetryAt?.let { it > now() } == true) return session.hasFetchedSnapshot

        val snapshot = try {
            remoteDataSource.fetchAll(owner)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            checkSession(session)
            if (suspendUntilReauthenticated(session, error)) return false
            session.restoreFailures = (session.restoreFailures + 1).coerceAtMost(13)
            session.restoreRetryAt = retryAt(session.restoreFailures)
            return session.hasFetchedSnapshot
        }

        val changes = commit(session) { store.applySnapshotWithoutChangingReservations(owner, snapshot) }
        var complete = true
        for (change in changes) {
            checkSession(session)
            if (!applyServerChange(change.local.alarm.id) {
                    commit(session) { store.applyServerChange(owner, change) }
                }
            ) {
                complete = false
            }
        }
        session.hasFetchedSnapshot = true
        session.restoreFailures = 0
        session.restoreRetryAt = if (complete) {
            commit(session) { store.markRestoreComplete(owner) }
            null
        } else {
            now() + NativeChangeRetryDelayMillis
        }
        return true
    }

    private suspend fun flushOutbox(session: PreparedSession): Long? {
        val owner = session.account.ownerAccountId
        while (true) {
            checkSession(session)
            val firstPerAlarm = store.pending(owner).distinctBy(AlarmOutboxEntity::remoteAlarmId)
            val pending = firstPerAlarm.filter { it.state == "PENDING" }
            val due = pending.filter { it.nextAttemptAt == null || it.nextAttemptAt <= now() }
            if (due.isEmpty()) return pending.mapNotNull(AlarmOutboxEntity::nextAttemptAt).minOrNull()

            for (candidate in due) {
                val attempted = commit(session) { store.startAttempt(candidate) } ?: continue
                if (!send(session, attempted)) return null
            }
        }
    }

    /** Returns false when authentication must be re-established before sending more work. */
    private suspend fun send(session: PreparedSession, entry: AlarmOutboxEntity): Boolean {
        checkSession(session)
        val request = entry.payload?.let { Json.decodeFromString<RemoteAlarm>(it) }
        val server = try {
            if (request == null) {
                remoteDataSource.delete(entry.ownerAccountId, entry.remoteAlarmId)
                null
            } else {
                remoteDataSource.upsert(entry.ownerAccountId, request)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            checkSession(session)
            if (suspendUntilReauthenticated(session, error)) return false
            val message = error.message ?: "알람 설정 동기화에 실패했습니다."
            commit(session) {
                if (error is ApiException && error.statusCode == 400) {
                    store.reject(entry, message)
                } else {
                    // Any ambiguous outcome must retain this exact PUT/DELETE ahead of newer work.
                    store.retry(entry, retryAt(entry.attemptCount), message)
                }
            }
            return true
        }

        if (request == null || server == null) {
            commit(session) { store.acknowledgeDelete(entry) }
            return true
        }
        val conflict = commit(session) { store.acknowledgeUpsert(entry, request, server) }
        if (conflict != null && !applyServerChange(conflict.alarm.id) {
                commit(session) { store.applyUpsertConflict(entry, conflict, server) }
            }
        ) {
            commit(session) {
                store.retry(
                    entry,
                    now() + NativeChangeRetryDelayMillis,
                    "알람 예약을 변경할 수 있을 때 다시 적용합니다.",
                )
            }
        }
        return true
    }

    private suspend fun <T> commit(session: PreparedSession, block: suspend () -> T): T =
        sessionGate.withSession(session.account.sessionIdentity) {
            store.transaction {
                checkSession(session)
                block().also { checkSession(session) }
            }
        }

    private suspend fun checkSession(expected: PreparedSession) {
        if (preparedSession.value !== expected) throw DestinationAccountChanged()
        checkAccount(expected.account)
    }

    private suspend fun checkAccount(expected: DestinationAccountContext) {
        val current = accountProvider.currentContext()
        if (current == null || current.sessionIdentity !== expected.sessionIdentity ||
            current.ownerAccountId != expected.ownerAccountId
        ) throw DestinationAccountChanged()
    }

    private fun suspendUntilReauthenticated(session: PreparedSession, error: Exception): Boolean {
        if (error !is ApiException || error.statusCode != 401) return false
        unauthorizedSession = session.account.sessionIdentity
        session.restoreRetryAt = null
        return true
    }

    private fun retryAt(attemptCount: Int): Long = now() + DestinationRetryPolicy.delayMillis(attemptCount)

    private class PreparedSession(val account: DestinationAccountContext) {
        var hasFetchedSnapshot = false
        var restoreFailures = 0
        var restoreRetryAt: Long? = null
    }

    private companion object {
        const val NativeChangeRetryDelayMillis = 5_000L
    }
}
