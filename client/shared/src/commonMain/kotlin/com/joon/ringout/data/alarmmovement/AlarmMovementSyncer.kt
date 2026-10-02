package com.joon.ringout.data.alarmmovement

import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxState
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncDao
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.domain.alarmmovement.AlarmMovementAction
import com.joon.ringout.domain.alarmmovement.AlarmMovementRepository
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceId
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceRetryPolicy
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/** 호출자가 계정 검증과 프로세스 공통 잠금을 제공한다. 알람 실행 PATCH와 별도로 재시도한다. */
internal class AlarmMovementSyncer(
    private val dao: AlarmOccurrenceSyncDao,
    private val repository: AlarmMovementRepository,
    private val now: () -> Long,
    private val checkAccount: suspend () -> Unit,
    private val onUnauthorized: () -> Nothing,
) {
    private var joinedRoomIds: List<Long>? = null

    suspend fun flush(owner: String): Long? {
        var nextRetry: Long? = null
        fun include(retry: Long?) { if (retry != null) nextRetry = nextRetry?.let { minOf(it, retry) } ?: retry }
        val executions = dao.getUnsentMovements(owner).map { it.localExecutionId }.distinct()
        for (executionId in executions) {
            checkAccount()
            // 알람 생성 POST가 완료되기 전에는 서버에 전달할 UUID가 없다.
            val serverId = dao.getExecution(owner, executionId)?.serverOccurrenceId ?: continue
            val occurrenceId = AlarmOccurrenceId(serverId)
            val parents = dao.getMovements(owner, executionId).filter { it.roomId == null && it.isUnsent() }
                .sortedBy { it.priority() }
            for (parent in parents) {
                val current = dao.getMovements(owner, executionId)
                if (parent.action != AlarmMovementAction.START_MOVEMENT && current.any {
                    it.roomId == null && it.action == AlarmMovementAction.START_MOVEMENT && it.isUnsent()
                }) continue
                include(deliver(owner, parent, occurrenceId))
            }
            val deliveries = dao.getMovements(owner, executionId).filter { it.roomId != null && it.isUnsent() }
                .sortedWith(compareBy<AlarmMovementOutboxEntity> { it.priority() }.thenBy { it.id })
            for (event in deliveries) {
                checkAccount()
                val current = dao.getMovements(owner, executionId)
                if (event.action != AlarmMovementAction.START_MOVEMENT && current.any {
                    it.action == AlarmMovementAction.START_MOVEMENT && it.isUnsent() &&
                        (it.roomId == null || it.roomId == event.roomId)
                }) continue
                // 종료 전송 후 늦게 들어온 알람 해제는 이동을 다시 시작하지 않는다.
                if (event.action == AlarmMovementAction.START_MOVEMENT && current.any {
                    it.roomId == event.roomId && it.action != AlarmMovementAction.START_MOVEMENT && !it.isUnsent()
                }) {
                    if (event.lastErrorCode != "ALREADY_ENDED") {
                        dao.saveMovementDelivery(owner, event.id, AlarmOccurrenceOutboxState.BLOCKED,
                            event.attemptCount, code = "ALREADY_ENDED")
                    }
                    continue
                }
                include(deliver(owner, event, occurrenceId))
            }
        }
        return nextRetry
    }

    private suspend fun deliver(owner: String, event: AlarmMovementOutboxEntity, occurrenceId: AlarmOccurrenceId): Long? {
        if (event.state == AlarmOccurrenceOutboxState.BLOCKED && event.lastErrorCode != "AUTH_REQUIRED") return null
        event.nextAttemptAtEpochMillis?.let { if (it > now()) return it }
        checkAccount()
        val attempt = (event.attemptCount.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        check(dao.saveMovementDelivery(owner, event.id, AlarmOccurrenceOutboxState.PENDING, attempt) == 1)
        val targets: List<Long>?
        try {
            targets = if (event.roomId == null) {
                joinedRoomIds ?: repository.getJoinedRoomIds().also { joinedRoomIds = it }
            } else {
                repository.changeMovement(event.roomId, occurrenceId, event.action)
                null
            }
            checkAccount()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            checkAccount()
            val unauthorized = error is ApiException && error.statusCode == 401
            val retryable = when (error) {
                is ApiException -> AlarmOccurrenceRetryPolicy.isRetryableHttpStatus(error.statusCode)
                is IOException, is HttpRequestTimeoutException -> true
                else -> false
            }
            val retryAt = if (retryable) now() + AlarmOccurrenceRetryPolicy.delayMillis(attempt) else null
            check(dao.saveMovementDelivery(owner, event.id,
                if (retryable) AlarmOccurrenceOutboxState.PENDING else AlarmOccurrenceOutboxState.BLOCKED,
                attempt, retryAt, if (unauthorized) "AUTH_REQUIRED" else (error as? ApiException)?.code
                    ?: if (retryable) "NETWORK_ERROR" else "INVALID_RESPONSE") == 1)
            if (unauthorized) onUnauthorized()
            return retryAt
        }
        // DB 반영 실패를 HTTP 실패로 덮어쓰지 않는다. 다음 실행이 같은 행을 복구한다.
        if (targets != null) dao.completeMovementTargets(owner, event.id, targets)
        else check(dao.saveMovementDelivery(owner, event.id, AlarmOccurrenceOutboxState.SENT, attempt) == 1)
        return null
    }
}

private fun AlarmMovementOutboxEntity.isUnsent() = state != AlarmOccurrenceOutboxState.SENT
private fun AlarmMovementOutboxEntity.priority() = if (action == AlarmMovementAction.START_MOVEMENT) 0 else 1
