package com.joon.ringout.data.destination

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.domain.auth.AuthSession
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** Sends durable saved-destination operations in per-ID order. */
internal class DestinationOutboxSyncer(
    private val dao: SavedDestinationDao,
    private val remoteDataSource: DestinationRemoteDataSource,
    private val accountProvider: DestinationAccountProvider,
    private val sessionGate: DestinationSyncSessionGate,
    private val authSession: AuthSession,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun flush(): Long? = processMutex.withLock {
        val account = accountProvider.currentContext() ?: return@withLock null
        if (unauthorizedSession === account.sessionIdentity) return@withLock null
        var nextRetryAt: Long? = null
        try {
            prepareAccount(account)
            nextRetryAt = restoreIfNeeded(account)
            val outboxRetryAt = flushOutbox(account)
            if (outboxRetryAt != null) {
                nextRetryAt = nextRetryAt?.let { minOf(it, outboxRetryAt) } ?: outboxRetryAt
            }
        } catch (_: DestinationAccountChanged) {
            return@withLock null
        }
        nextRetryAt
    }

    suspend fun prepareAccountForCurrentSession() {
        val account = accountProvider.currentContext() ?: return
        prepareAccount(account)
    }

    private suspend fun prepareAccount(account: DestinationAccountContext) {
        sessionGate.withSession(account.sessionIdentity) {
            checkAccount(account)
            dao.prepareAccount(account.ownerAccountId)
        }
    }

    private suspend fun restoreIfNeeded(account: DestinationAccountContext): Long? {
        val state = dao.getSyncState(account.ownerAccountId)
        if (state?.restoreComplete == true) return null
        val retryAt = state?.restoreNextAttemptAtEpochMillis
        if (retryAt != null && retryAt > now()) return retryAt

        val attempt = sessionGate.withSession(account.sessionIdentity) {
            checkAccount(account)
            dao.recordRestoreAttempt(account.ownerAccountId)
        }
        try {
            checkAccount(account)
            val remoteDestinations = remoteDataSource.fetchAll(account.ownerAccountId)
            sessionGate.withSession(account.sessionIdentity) {
                checkAccount(account)
                dao.applyRemoteSnapshot(account.ownerAccountId, remoteDestinations)
            }
            return null
        } catch (error: DestinationAccountChanged) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val unauthorized = error is ApiException && error.statusCode == 401
            val retryable = error.isRetryable()
            val nextAttemptAt = if (retryable) now() + DestinationRetryPolicy.delayMillis(attempt) else null
            try {
                sessionGate.withSession(account.sessionIdentity) {
                    checkAccount(account)
                    dao.recordRestoreFailure(
                        ownerAccountId = account.ownerAccountId,
                        nextAttemptAtEpochMillis = nextAttemptAt,
                        errorCode = (error as? ApiException)?.code
                            ?: if (retryable) "NETWORK_ERROR" else "INVALID_RESPONSE",
                        errorMessage = error.message ?: "목적지 복원에 실패했습니다.",
                    )
                }
            } catch (_: DestinationAccountChanged) {
                throw DestinationAccountChanged()
            }
            if (unauthorized) {
                unauthorizedSession = account.sessionIdentity
                return null
            }
            return nextAttemptAt
        }
    }

    private suspend fun flushOutbox(account: DestinationAccountContext): Long? {
        while (true) {
            checkAccount(account)
            val due = dao.getDueOutbox(account.ownerAccountId, now())
            if (due.isEmpty()) return dao.getNextOutboxRetryAt(account.ownerAccountId)
            for (pending in due) {
                checkAccount(account)
                val operation = sessionGate.withSession(account.sessionIdentity) {
                    checkAccount(account)
                    dao.recordOutboxAttempt(account.ownerAccountId, pending.id)
                } ?: continue
                try {
                    send(account, operation)
                } catch (error: DestinationAccountChanged) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (error is ApiException && error.statusCode == 401) {
                        unauthorizedSession = account.sessionIdentity
                        return null
                    }
                    val retryable = error.isRetryable()
                    if (retryable) {
                        val retryAt = now() + DestinationRetryPolicy.delayMillis(operation.attemptCount)
                        sessionGate.withSession(account.sessionIdentity) {
                            checkAccount(account)
                            dao.retryOutbox(
                                ownerAccountId = account.ownerAccountId,
                                id = operation.id,
                                nextAttemptAtEpochMillis = retryAt,
                                errorCode = (error as? ApiException)?.code ?: "NETWORK_ERROR",
                                errorMessage = error.message ?: "목적지 동기화에 실패했습니다.",
                            )
                        }
                    } else {
                        sessionGate.withSession(account.sessionIdentity) {
                            checkAccount(account)
                            dao.blockOutbox(
                                ownerAccountId = account.ownerAccountId,
                                id = operation.id,
                                errorCode = (error as? ApiException)?.code ?: "INVALID_RESPONSE",
                                errorMessage = error.message ?: "목적지 동기화를 확인할 수 없습니다.",
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun send(account: DestinationAccountContext, operation: DestinationOutboxEntity) {
        checkAccount(account)
        when (operation.operation) {
            DestinationOutboxOperation.UPSERT -> {
                val request = RemoteSavedDestination(
                    id = operation.remoteDestinationId,
                    alias = checkNotNull(operation.payloadAlias),
                    latitude = checkNotNull(operation.payloadLatitude),
                    longitude = checkNotNull(operation.payloadLongitude),
                    version = checkNotNull(operation.destinationVersion),
                )
                val response = remoteDataSource.upsert(account.ownerAccountId, request)
                checkAccount(account)
                check(response.id == request.id) {
                    "목적지 upsert 응답의 ID가 요청한 ID와 다릅니다."
                }
                sessionGate.withSession(account.sessionIdentity) {
                    checkAccount(account)
                    dao.acknowledgeUpsertOutbox(
                        ownerAccountId = account.ownerAccountId,
                        outboxId = operation.id,
                        remoteDestinationId = request.id,
                        serverDestination = response,
                    )
                }
                return
            }

            DestinationOutboxOperation.DELETE -> {
                remoteDataSource.delete(account.ownerAccountId, operation.remoteDestinationId)
                checkAccount(account)
            }
        }
        sessionGate.withSession(account.sessionIdentity) {
            checkAccount(account)
            dao.acknowledgeOutbox(account.ownerAccountId, operation.id)
        }
    }

    private suspend fun checkAccount(expected: DestinationAccountContext) {
        val current = accountProvider.currentContext()
        if (current == null || current.ownerAccountId != expected.ownerAccountId ||
            current.sessionIdentity !== expected.sessionIdentity ||
            authSession.identity.value !== expected.sessionIdentity
        ) {
            throw DestinationAccountChanged()
        }
    }

    private fun Exception.isRetryable(): Boolean = when (this) {
        is IOException, is HttpRequestTimeoutException -> true
        is ApiException -> statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500
        else -> false
    }

    private companion object {
        private val processMutex = Mutex()
        private var unauthorizedSession: Any? = null
    }
}

internal object DestinationRetryPolicy {
    fun delayMillis(attemptCount: Int): Long =
        (1_000L shl (attemptCount - 1).coerceIn(0, 12)).coerceAtMost(MaxDelayMillis)

    private const val MaxDelayMillis = 60 * 60 * 1_000L
}
