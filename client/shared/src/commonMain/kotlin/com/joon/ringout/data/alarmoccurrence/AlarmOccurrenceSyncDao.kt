package com.joon.ringout.data.alarmoccurrence

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceId
import kotlinx.coroutines.flow.Flow

/** 저장만 담당한다. 전송 가능 여부 판단, HTTP 요청, 재시도 스케줄링은 전송 계층에서 처리한다. */
@Dao
abstract class AlarmOccurrenceSyncDao {
    // 실행/울림의 누락 시각 보완도 전송기에 알려야 하므로 세 테이블의 변경을 관찰한다.
    @Query("""
        SELECT o.* FROM alarm_occurrence_outbox o
        JOIN alarm_occurrence_sync e ON e.local_execution_id = o.local_execution_id
        JOIN alarm_occurrence_ringing_links r ON r.local_ringing_id = o.local_ringing_id
        WHERE o.state != 'SENT' ORDER BY o.id
    """)
    abstract fun observeUnsentEvents(): Flow<List<AlarmOccurrenceOutboxEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertExecution(execution: AlarmOccurrenceSyncEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRinging(ringing: AlarmOccurrenceRingingLinkEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEvent(event: AlarmOccurrenceOutboxEntity)

    @Query("""
        UPDATE alarm_occurrence_sync SET scheduled_at = COALESCE(scheduled_at, :scheduledAt),
            started_at = COALESCE(started_at, :startedAt)
        WHERE local_execution_id = :executionId AND owner_account_id = :ownerAccountId
    """)
    protected abstract suspend fun fillMissingStartTimes(ownerAccountId: String, executionId: String, scheduledAt: Long?, startedAt: Long?)

    @Query("UPDATE alarm_occurrence_ringing_links SET ringing_at = COALESCE(ringing_at, :at) WHERE local_ringing_id = :ringingId")
    protected abstract suspend fun fillMissingRingingTime(ringingId: String, at: Long?)

    @Query("""
        UPDATE alarm_occurrence_outbox SET occurred_at = :at
        WHERE local_ringing_id = :ringingId AND kind IN ('START', 'REPEAT_RANG') AND occurred_at IS NULL AND state != 'SENT'
    """)
    protected abstract suspend fun fillMissingEventTime(ringingId: String, at: Long?)

    @Query("SELECT * FROM alarm_occurrence_sync WHERE owner_account_id = :ownerAccountId AND local_execution_id = :executionId")
    abstract suspend fun getExecution(ownerAccountId: String, executionId: String): AlarmOccurrenceSyncEntity?

    @Query("""
        SELECT r.* FROM alarm_occurrence_ringing_links r
        JOIN alarm_occurrence_sync e ON e.local_execution_id = r.local_execution_id
        WHERE e.owner_account_id = :ownerAccountId AND r.local_ringing_id = :ringingId
    """)
    abstract suspend fun getRinging(ownerAccountId: String, ringingId: String): AlarmOccurrenceRingingLinkEntity?

    @Query("""
        SELECT e.* FROM alarm_occurrence_sync e
        JOIN alarm_occurrence_ringing_links r ON r.local_execution_id = e.local_execution_id
        WHERE e.owner_account_id = :ownerAccountId AND r.local_ringing_id = :ringingId
    """)
    abstract suspend fun getExecutionForRinging(ownerAccountId: String, ringingId: String): AlarmOccurrenceSyncEntity?

    @Query("""
        SELECT o.* FROM alarm_occurrence_outbox o
        JOIN alarm_occurrence_sync e ON e.local_execution_id = o.local_execution_id
        WHERE e.owner_account_id = :ownerAccountId AND e.local_execution_id = :executionId ORDER BY o.id
    """)
    abstract suspend fun getEvents(ownerAccountId: String, executionId: String): List<AlarmOccurrenceOutboxEntity>

    /** BLOCKED도 반환해 필수 시각 미확보 및 영구 오류를 네트워크 대기와 구분할 수 있게 한다. */
    @Query("""
        SELECT o.* FROM alarm_occurrence_outbox o
        JOIN alarm_occurrence_sync e ON e.local_execution_id = o.local_execution_id
        WHERE e.owner_account_id = :ownerAccountId AND o.state != 'SENT' ORDER BY o.id
    """)
    abstract suspend fun getUnsentEvents(ownerAccountId: String): List<AlarmOccurrenceOutboxEntity>

    /** 실행, 최초 울림 연결, POST 대기를 한 번에 저장한다. 재전달은 최초 값을 보존한다. */
    @Transaction
    open suspend fun recordStart(execution: AlarmOccurrenceSyncEntity) {
        require(execution.localExecutionId.isNotBlank() && execution.ownerAccountId.isNotBlank())
        require(execution.alarmId.isNotBlank() && execution.alarmId.length <= 64)
        require(execution.scheduleVersion > 0 && execution.serverOccurrenceId == null)
        insertExecution(execution)
        val previous = checkNotNull(getExecution(execution.ownerAccountId, execution.localExecutionId)) {
            "다른 계정에 귀속된 실행은 재사용할 수 없습니다."
        }
        check(previous.alarmId == execution.alarmId && previous.scheduleVersion == execution.scheduleVersion)
        // 실제로 확인된 시각이 늦게 도착하면 비어 있던 값만 보완한다. 보류 해제 여부는 전송 계층에서 판단한다.
        fillMissingStartTimes(execution.ownerAccountId, execution.localExecutionId,
            execution.scheduledAtEpochMillis, execution.startedAtEpochMillis)
        val stored = checkNotNull(getExecution(execution.ownerAccountId, execution.localExecutionId))
        insertRinging(AlarmOccurrenceRingingLinkEntity(
            localRingingId = stored.localExecutionId,
            localExecutionId = stored.localExecutionId,
            eventId = null,
            ringingAtEpochMillis = stored.startedAtEpochMillis,
        ))
        val ringing = checkNotNull(getRinging(stored.ownerAccountId, stored.localExecutionId))
        check(ringing.localExecutionId == stored.localExecutionId && ringing.eventId == null)
        fillMissingRingingTime(ringing.localRingingId, stored.startedAtEpochMillis)
        insertEvent(event(stored.localExecutionId, ringing.localRingingId, "start", AlarmOccurrenceOutboxKind.START, stored.startedAtEpochMillis))
        fillMissingEventTime(ringing.localRingingId, stored.startedAtEpochMillis)
    }

    @Transaction
    open suspend fun recordRepeat(
        ownerAccountId: String,
        executionId: String,
        localRingingId: String,
        eventId: String,
        ringingAtEpochMillis: Long?,
    ): AlarmOccurrenceRingingLinkEntity {
        checkNotNull(getExecution(ownerAccountId, executionId))
        require(localRingingId.isNotBlank() && localRingingId != executionId)
        require(eventId.isNotBlank() && eventId.length <= 64)
        insertRinging(AlarmOccurrenceRingingLinkEntity(localRingingId, executionId, eventId, ringingAtEpochMillis))
        val previous = checkNotNull(getRinging(ownerAccountId, localRingingId))
        check(previous.localExecutionId == executionId && previous.eventId != null)
        fillMissingRingingTime(localRingingId, ringingAtEpochMillis)
        val stored = checkNotNull(getRinging(ownerAccountId, localRingingId))
        insertEvent(event(executionId, localRingingId, "repeat:$localRingingId", AlarmOccurrenceOutboxKind.REPEAT_RANG, stored.ringingAtEpochMillis))
        fillMissingEventTime(localRingingId, stored.ringingAtEpochMillis)
        return stored
    }

    @Transaction
    open suspend fun recordDismissal(ownerAccountId: String, localRingingId: String, dismissedAtEpochMillis: Long) {
        val ringing = checkNotNull(getRinging(ownerAccountId, localRingingId))
        val kind = if (ringing.eventId == null) AlarmOccurrenceOutboxKind.INITIAL_DISMISSED else AlarmOccurrenceOutboxKind.REPEAT_DISMISSED
        insertEvent(event(ringing.localExecutionId, localRingingId, "dismiss:$localRingingId", kind, dismissedAtEpochMillis))
    }

    /** 두 종료 결과가 경합해도 실행당 처음 저장된 결과만 보존한다. */
    @Transaction
    open suspend fun recordTerminal(
        ownerAccountId: String,
        localRingingId: String,
        kind: AlarmOccurrenceOutboxKind,
        occurredAtEpochMillis: Long,
    ) {
        require(kind == AlarmOccurrenceOutboxKind.ARRIVED || kind == AlarmOccurrenceOutboxKind.FORCE_ENDED)
        val ringing = checkNotNull(getRinging(ownerAccountId, localRingingId))
        insertEvent(event(ringing.localExecutionId, localRingingId, "terminal", kind, occurredAtEpochMillis))
    }

    @Query("""
        UPDATE alarm_occurrence_sync SET server_occurrence_id = :serverId
        WHERE local_execution_id = :executionId AND owner_account_id = :ownerAccountId
            AND (server_occurrence_id IS NULL OR server_occurrence_id = :serverId)
    """)
    protected abstract suspend fun updateServerId(ownerAccountId: String, executionId: String, serverId: String): Int

    /** 서버 ID 저장과 POST 완료 처리를 한 트랜잭션으로 수행한다. */
    @Transaction
    open suspend fun completeStart(ownerAccountId: String, executionId: String, serverId: String) {
        AlarmOccurrenceId(serverId)
        check(updateServerId(ownerAccountId, executionId, serverId) == 1) { "실행의 계정 또는 서버 ID가 일치하지 않습니다." }
        markStartSent(executionId)
    }

    @Query("""
        UPDATE alarm_occurrence_outbox SET state = 'SENT', next_attempt_at = NULL,
            last_error_code = NULL, last_error_message = NULL
        WHERE local_execution_id = :executionId AND kind = 'START'
    """)
    protected abstract suspend fun markStartSent(executionId: String)

    @Query("""
        UPDATE alarm_occurrence_outbox SET state = :state, attempt_count = :attemptCount,
            next_attempt_at = :nextAttemptAtEpochMillis, last_error_code = :errorCode, last_error_message = :errorMessage
        WHERE id = :eventId AND state != 'SENT' AND attempt_count <= :attemptCount
            AND local_execution_id IN (SELECT local_execution_id FROM alarm_occurrence_sync WHERE owner_account_id = :ownerAccountId)
            AND (kind != 'START' OR :state != 'SENT')
    """)
    protected abstract suspend fun updateDelivery(
        ownerAccountId: String, eventId: Long, state: AlarmOccurrenceOutboxState, attemptCount: Int,
        nextAttemptAtEpochMillis: Long?, errorCode: String?, errorMessage: String?,
    ): Int

    open suspend fun saveDeliveryState(
        ownerAccountId: String,
        eventId: Long,
        state: AlarmOccurrenceOutboxState,
        attemptCount: Int,
        nextAttemptAtEpochMillis: Long? = null,
        errorCode: String? = null,
        errorMessage: String? = null,
    ): Boolean {
        require(attemptCount >= 0)
        return updateDelivery(ownerAccountId, eventId, state, attemptCount,
            if (state == AlarmOccurrenceOutboxState.PENDING) nextAttemptAtEpochMillis else null,
            if (state == AlarmOccurrenceOutboxState.SENT) null else errorCode,
            if (state == AlarmOccurrenceOutboxState.SENT) null else errorMessage) == 1
    }
}

private fun event(executionId: String, ringingId: String, key: String, kind: AlarmOccurrenceOutboxKind, at: Long?) =
    AlarmOccurrenceOutboxEntity(localExecutionId = executionId, localRingingId = ringingId,
        deduplicationKey = key, kind = kind, occurredAtEpochMillis = at)
