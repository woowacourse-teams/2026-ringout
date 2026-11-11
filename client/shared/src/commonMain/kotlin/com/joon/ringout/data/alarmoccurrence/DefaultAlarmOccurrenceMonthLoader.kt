package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.data.destination.DestinationAccountChanged
import com.joon.ringout.data.destination.DestinationSyncSessionGate
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceMonthLoader
import com.joon.ringout.domain.alarmoccurrence.currentAlarmOccurrenceMonth
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultAlarmOccurrenceMonthLoader(
    httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession,
    private val dao: AlarmOccurrenceCacheDao,
    private val sessionGate: DestinationSyncSessionGate,
    private val applicationScope: CoroutineScope,
    private val currentMonth: () -> MissionYearMonth = { currentAlarmOccurrenceMonth() },
) : AlarmOccurrenceMonthLoader {
    private val remote = AlarmOccurrenceMonthRemoteDataSource(httpClient, tokenStorage, authSession)
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<RequestKey, Deferred<Boolean>>()

    override suspend fun ensure(month: MissionYearMonth): Boolean = retryOnFailure {
        val monthKey = month.toCacheKey()
        if (monthKey > currentMonth().toCacheKey()) return@retryOnFailure true
        if (authSession.state.value != AuthSessionState.Authenticated) return@retryOnFailure true
        val account = captureAlarmOccurrenceAccount(tokenStorage, authSession) ?: return@retryOnFailure false
        val key = RequestKey(account, month)
        val request = mutex.withLock {
            inFlight[key] ?: applicationScope.async(start = CoroutineStart.LAZY) {
                try {
                    retryOnFailure { load(account, month) }
                } finally {
                    withContext(NonCancellable) {
                        mutex.withLock { inFlight.remove(key) }
                    }
                }
            }.also {
                inFlight[key] = it
                it.start()
            }
        }
        request.await()
    }

    private suspend fun load(account: AlarmOccurrenceAccount, month: MissionYearMonth): Boolean {
        val monthKey = month.toCacheKey()
        val complete = sessionGate.withSession(account.sessionIdentity) {
            checkAccount(account)
            dao.isComplete(account.ownerAccountId, monthKey)
        }
        if (complete) return true

        val snapshot = remote.fetch(account, month)
        sessionGate.withSession(account.sessionIdentity) {
            checkAccount(account)
            dao.saveMonth(account.ownerAccountId, monthKey, snapshot.occurrences, snapshot.ringings)
        }
        return true
    }

    private suspend fun checkAccount(account: AlarmOccurrenceAccount) {
        if (captureAlarmOccurrenceAccount(tokenStorage, authSession) != account) throw AlarmOccurrenceAccountChanged()
    }

    private data class RequestKey(val account: AlarmOccurrenceAccount, val month: MissionYearMonth)
}

private inline fun retryOnFailure(block: () -> Boolean): Boolean = try {
    block()
} catch (_: AlarmOccurrenceAccountChanged) {
    false
} catch (_: DestinationAccountChanged) {
    false
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    false
}
