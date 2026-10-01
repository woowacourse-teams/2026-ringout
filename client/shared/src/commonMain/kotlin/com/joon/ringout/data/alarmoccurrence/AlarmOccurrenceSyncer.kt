package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrence
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceEvent
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceId
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceRepository
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceRetryPolicy
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceStart
import com.joon.ringout.domain.alarmoccurrence.AlarmRepeatRinging
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant

/** 한 번 대기열을 처리한다. 플랫폼 Worker와 앱 실행 중 재시도 루프가 같은 전송기를 사용한다. */
internal class AlarmOccurrenceSyncer(
    private val dao: AlarmOccurrenceSyncDao,
    httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val repositoryFactory: (AlarmOccurrenceAccount) -> AlarmOccurrenceRepository = { account ->
        DefaultAlarmOccurrenceRepository(httpClient, tokenStorage, authSession, account)
    },
) {
    private var unauthorizedSession: Any? = null

    /** 반환 시각은 실행별 첫 미전송 이벤트의 다음 재시도 시각이다. null이면 외부 변경을 기다린다. */
    suspend fun flush(): Long? = processMutex.withLock {
        val account = captureAlarmOccurrenceAccount(tokenStorage, authSession) ?: return@withLock null
        if (unauthorizedSession === account.sessionIdentity) return@withLock null
        val repository = repositoryFactory(account)
        var nextRetryAt: Long? = null
        val executions = dao.getUnsentEvents(account.ownerAccountId).map { it.localExecutionId }.distinct()
        try {
            for (executionId in executions) {
                val next = drainExecution(account, executionId, repository)
                if (next != null) nextRetryAt = nextRetryAt?.let { minOf(it, next) } ?: next
            }
        } catch (_: AlarmOccurrenceAccountChanged) {
            // 응답이 유실된 것처럼 남긴다. 원래 계정으로 돌아오면 같은 식별자로 재전송한다.
            return@withLock null
        }
        nextRetryAt
    }

    private suspend fun checkAccount(account: AlarmOccurrenceAccount) {
        account.checkSession(authSession)
        val current = captureAlarmOccurrenceAccount(tokenStorage, authSession)
        if (current != account) throw AlarmOccurrenceAccountChanged()
    }

    private suspend fun drainExecution(
        account: AlarmOccurrenceAccount,
        executionId: String,
        repository: AlarmOccurrenceRepository,
    ): Long? {
        while (true) {
            checkAccount(account)
            val events = dao.getEvents(account.ownerAccountId, executionId)
            val event = events.filter { it.state != AlarmOccurrenceOutboxState.SENT }
                .minWithOrNull(compareBy<AlarmOccurrenceOutboxEntity> { it.kind.priority() }.thenBy { it.id }) ?: return null
            if (event.state == AlarmOccurrenceOutboxState.BLOCKED && event.lastErrorCode !in RecoverableBlocks) return null
            if (events.any { it.kind.isTerminal() && it.state == AlarmOccurrenceOutboxState.SENT }) {
                block(account, event, "ALREADY_ENDED", "이미 종료된 실행의 후속 이벤트입니다.")
                return null
            }
            val execution = checkNotNull(dao.getExecution(account.ownerAccountId, executionId))
            val ringing = dao.getRinging(account.ownerAccountId, event.localRingingId)
            val request = try {
                prepare(event, execution, ringing)
            } catch (error: MissingOccurrenceTime) {
                block(account, event, "MISSING_TIME", error.message.orEmpty())
                return null
            } catch (error: IllegalArgumentException) {
                block(account, event, "INVALID_LOCAL_EVENT", error.message.orEmpty())
                return null
            } catch (error: IllegalStateException) {
                block(account, event, "INVALID_LOCAL_EVENT", error.message.orEmpty())
                return null
            }
            val due = event.nextAttemptAtEpochMillis
            if (due != null && due > now()) return due
            checkAccount(account)
            val attempt = (event.attemptCount.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            // 통신 전 시도 횟수를 저장한다. 프로세스 종료 후에는 같은 본문으로 다시 보낼 수 있다.
            check(dao.saveDeliveryState(account.ownerAccountId, event.id, AlarmOccurrenceOutboxState.PENDING, attempt))
            val response: AlarmOccurrence
            try {
                response = when (request) {
                    is Prepared.Start -> repository.start(request.start)
                    is Prepared.Event -> repository.recordEvent(request.id, request.event)
                }
                checkAccount(account)
                if (request is Prepared.Event) check(response.id == request.id) { "응답의 알람 실행 ID가 일치하지 않습니다." }
            } catch (error: AlarmOccurrenceAccountChanged) {
                throw error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val isUnauthorized = error is ApiException && error.statusCode == 401
                val retryable = when (error) {
                    is ApiException -> AlarmOccurrenceRetryPolicy.isRetryableHttpStatus(error.statusCode)
                    is IOException, is HttpRequestTimeoutException -> true
                    else -> false
                }
                val retryAt = if (retryable) now() + AlarmOccurrenceRetryPolicy.delayMillis(attempt) else null
                dao.saveDeliveryState(
                    account.ownerAccountId, event.id,
                    if (retryable) AlarmOccurrenceOutboxState.PENDING else AlarmOccurrenceOutboxState.BLOCKED,
                    attempt, retryAt,
                    if (isUnauthorized) "AUTH_REQUIRED" else (error as? ApiException)?.code ?: if (retryable) "NETWORK_ERROR" else "INVALID_RESPONSE",
                    if (error is ApiException) error.apiMessage else "알람 이벤트 전송에 실패했습니다.",
                )
                if (isUnauthorized) {
                    unauthorizedSession = account.sessionIdentity
                    throw AlarmOccurrenceAccountChanged()
                }
                return retryAt
            }
            // DB 실패는 전송 오류로 분류하지 않는다. 응답 반영에 실패하면 대기 행이 유지된다.
            if (request is Prepared.Start) {
                dao.completeStart(account.ownerAccountId, executionId, response.id.value)
            } else {
                check(dao.saveDeliveryState(account.ownerAccountId, event.id, AlarmOccurrenceOutboxState.SENT, attempt))
            }
        }
    }

    private suspend fun block(account: AlarmOccurrenceAccount, event: AlarmOccurrenceOutboxEntity, code: String, message: String) {
        if (event.state == AlarmOccurrenceOutboxState.BLOCKED && event.lastErrorCode == code) return
        dao.saveDeliveryState(account.ownerAccountId, event.id, AlarmOccurrenceOutboxState.BLOCKED,
            event.attemptCount, errorCode = code, errorMessage = message)
    }

    companion object {
        // 앱 루프와 플랫폼 Worker 등 여러 인스턴스도 같은 프로세스에서 중복 전송하지 않는다.
        private val processMutex = Mutex()
        private val RecoverableBlocks = setOf("MISSING_TIME", "MISSING_STARTED_AT", "AUTH_REQUIRED")
    }
}

private class MissingOccurrenceTime(message: String) : Exception(message)
private fun Long?.requiredTime(): Instant = this?.let(Instant::fromEpochMilliseconds)
    ?: throw MissingOccurrenceTime("실제로 확인된 발생 시각 또는 예정 시각이 필요합니다.")

private sealed interface Prepared {
    data class Start(val start: AlarmOccurrenceStart) : Prepared
    data class Event(val id: AlarmOccurrenceId, val event: AlarmOccurrenceEvent) : Prepared
}

private fun prepare(
    event: AlarmOccurrenceOutboxEntity,
    execution: AlarmOccurrenceSyncEntity,
    ringing: AlarmOccurrenceRingingLinkEntity?,
): Prepared {
    if (event.kind == AlarmOccurrenceOutboxKind.START) return Prepared.Start(AlarmOccurrenceStart(
        execution.alarmId, execution.scheduledAtEpochMillis.requiredTime(), execution.startedAtEpochMillis.requiredTime(),
    ))
    val id = AlarmOccurrenceId(checkNotNull(execution.serverOccurrenceId) { "POST가 완료되지 않았습니다." })
    val at = event.occurredAtEpochMillis.requiredTime()
    fun repeat(): AlarmRepeatRinging {
        val link = checkNotNull(ringing)
        check(link.localExecutionId == execution.localExecutionId)
        return AlarmRepeatRinging(checkNotNull(link.eventId), link.ringingAtEpochMillis.requiredTime())
    }
    val update = when (event.kind) {
        AlarmOccurrenceOutboxKind.INITIAL_DISMISSED -> AlarmOccurrenceEvent.InitialDismissed(at)
        AlarmOccurrenceOutboxKind.REPEAT_RANG -> AlarmOccurrenceEvent.RepeatRang(repeat())
        AlarmOccurrenceOutboxKind.REPEAT_DISMISSED -> AlarmOccurrenceEvent.RepeatDismissed(repeat(), at)
        AlarmOccurrenceOutboxKind.ARRIVED -> AlarmOccurrenceEvent.Arrived(at)
        AlarmOccurrenceOutboxKind.FORCE_ENDED -> AlarmOccurrenceEvent.ForceEnded(at)
        AlarmOccurrenceOutboxKind.START -> error("최초 울림은 POST로 처리합니다.")
    }
    return Prepared.Event(id, update)
}

private fun AlarmOccurrenceOutboxKind.isTerminal() = this == AlarmOccurrenceOutboxKind.ARRIVED || this == AlarmOccurrenceOutboxKind.FORCE_ENDED
private fun AlarmOccurrenceOutboxKind.priority() = when {
    this == AlarmOccurrenceOutboxKind.START -> 0
    isTerminal() -> 2
    else -> 1
}
