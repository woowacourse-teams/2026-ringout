package com.joon.ringout.alarm

import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxKind
import com.joon.ringout.data.alarmoccurrence.IosAlarmCleanupActivityProjection
import com.joon.ringout.data.alarmoccurrence.IosAlarmCleanupHistoryProjection
import com.joon.ringout.data.alarmoccurrence.IosAlarmCleanupMovementProjection
import com.joon.ringout.data.alarmoccurrence.IosAlarmCleanupOutboxProjection
import com.joon.ringout.data.alarmoccurrence.IosAlarmCleanupReadinessRequest
import com.joon.ringout.domain.alarmmovement.AlarmMovementAction

/** Immutable native event data and the native revision used for compare-and-delete. */
data class IosAlarmNativeCleanupEventCandidate(
    val eventId: String,
    val fingerprint: String,
    val alarmId: String,
    val occurrenceId: String,
    val action: IosAlarmMissionAction,
    val occurredAtEpochMillis: Long,
    val retryAttempt: Int,
    val ringingObservedAtEpochMillis: Long? = null,
    val ringingStoppedAtEpochMillis: Long? = null,
    val scheduleVersion: Long = 1,
    val ownerAccountId: String? = null,
    val ownerCaptured: Boolean = false,
    val chainId: String? = null,
    val consumedAtEpochMillis: Long? = null,
    val recordProjectionAckAtEpochMillis: Long? = null,
) {
    val requiresRecordProjection: Boolean
        get() = action == IosAlarmMissionAction.STOP ||
            ringingObservedAtEpochMillis != null || ringingStoppedAtEpochMillis != null

    fun roomReadinessRequest(): IosAlarmCleanupReadinessRequest? {
        val chain = chainId?.takeIf(String::isNotBlank) ?: return null
        if (!requiresRecordProjection) return null
        val expectedOutbox = buildList {
            add(IosAlarmCleanupOutboxProjection(
                occurrenceId = chain,
                deduplicationKey = "start",
                kind = AlarmOccurrenceOutboxKind.START,
                occurredAtEpochMillis = null,
            ))
            if (retryAttempt > 0 && (ringingObservedAtEpochMillis != null || ringingStoppedAtEpochMillis != null)) {
                add(IosAlarmCleanupOutboxProjection(
                    occurrenceId = occurrenceId,
                    deduplicationKey = "repeat:$occurrenceId",
                    kind = AlarmOccurrenceOutboxKind.REPEAT_RANG,
                    occurredAtEpochMillis = null,
                ))
            }
            ringingStoppedAtEpochMillis?.let { stoppedAt ->
                add(IosAlarmCleanupOutboxProjection(
                    occurrenceId = occurrenceId,
                    deduplicationKey = "dismiss:$occurrenceId",
                    kind = if (retryAttempt == 0) {
                        AlarmOccurrenceOutboxKind.INITIAL_DISMISSED
                    } else {
                        AlarmOccurrenceOutboxKind.REPEAT_DISMISSED
                    },
                    occurredAtEpochMillis = stoppedAt,
                ))
            }
        }
        return IosAlarmCleanupReadinessRequest(
            chainId = chain,
            rootOccurrenceId = if (retryAttempt == 0) occurrenceId else chain,
            alarmId = alarmId,
            scheduleVersion = scheduleVersion,
            expectedOutbox = expectedOutbox,
            expectedActivity = listOf(
                IosAlarmCleanupActivityProjection(
                    occurrenceId = occurrenceId,
                    alarmId = alarmId,
                    scheduleVersion = scheduleVersion,
                    ringingStartedAtEpochMillis = ringingObservedAtEpochMillis,
                    ringingStoppedAtEpochMillis = ringingStoppedAtEpochMillis,
                    ringingStartObserved = ringingObservedAtEpochMillis != null,
                ),
            ),
        )
    }
}

data class IosAlarmNativeCleanupChainCandidate(
    val chainId: String,
    val fingerprint: String,
    val sourceAlarmId: String,
    val scheduleVersion: Long,
    val rootOccurrenceId: String,
    val terminalOccurrenceId: String,
    val terminalOutcome: IosAlarmRecoveryAttemptOutcome,
    val terminalAtEpochMillis: Long,
    val terminalRecordAck: Boolean,
    val attempts: List<IosAlarmRecoveryAttemptDto>,
) {
    fun roomReadinessRequest(): IosAlarmCleanupReadinessRequest? {
        if (terminalOutcome == IosAlarmRecoveryAttemptOutcome.ACTIVE ||
            terminalOutcome == IosAlarmRecoveryAttemptOutcome.EXPIRED
        ) return null
        if (attempts.isEmpty() || attempts.any { it.chainId != chainId }) return null
        val unsupportedOverlap = terminalOutcome == IosAlarmRecoveryAttemptOutcome.SKIPPED_UNSUPPORTED_OVERLAP
        if (!unsupportedOverlap && !terminalRecordAck) return null
        if (attempts.any { !it.recordProjectionAck }) return null
        if (attempts.any { attempt ->
                !attempt.currentReservation.scheduleCallSettled ||
                    attempt.currentReservation.cancellationRequired ||
                    attempt.currentReservation.status in setOf(
                        IosAlarmRecoveryReservationStatus.PENDING,
                        IosAlarmRecoveryReservationStatus.IN_FLIGHT,
                        IosAlarmRecoveryReservationStatus.UNKNOWN,
                        IosAlarmRecoveryReservationStatus.CANCELLATION_REQUIRED,
                    )
            }
        ) return null

        val outbox = buildList {
            add(IosAlarmCleanupOutboxProjection(
                occurrenceId = rootOccurrenceId,
                deduplicationKey = "start",
                kind = AlarmOccurrenceOutboxKind.START,
                occurredAtEpochMillis = null,
            ))
            attempts.forEach { attempt ->
                val scheduledAt = attempt.scheduledAtEpochMillis
                    ?: attempt.snapshot.scheduledAtEpochMillis
                    ?: attempt.snapshot.originalTime?.let { time ->
                        val reference = attempt.stopAtEpochMillis
                        iosInitialRingingScheduledAt(time, reference)
                    }
                if (attempt.sequence > 0) {
                    add(IosAlarmCleanupOutboxProjection(
                        occurrenceId = attempt.occurrenceId,
                        deduplicationKey = "repeat:${attempt.occurrenceId}",
                        kind = AlarmOccurrenceOutboxKind.REPEAT_RANG,
                        occurredAtEpochMillis = scheduledAt,
                    ))
                }
                add(IosAlarmCleanupOutboxProjection(
                    occurrenceId = attempt.occurrenceId,
                    deduplicationKey = "dismiss:${attempt.occurrenceId}",
                    kind = if (attempt.sequence == 0) {
                        AlarmOccurrenceOutboxKind.INITIAL_DISMISSED
                    } else {
                        AlarmOccurrenceOutboxKind.REPEAT_DISMISSED
                    },
                    occurredAtEpochMillis = attempt.stopAtEpochMillis,
                ))
            }
            if (!unsupportedOverlap) {
                add(IosAlarmCleanupOutboxProjection(
                    occurrenceId = terminalOccurrenceId,
                    deduplicationKey = "terminal",
                    kind = if (terminalOutcome == IosAlarmRecoveryAttemptOutcome.TERMINAL_SUCCEEDED) {
                        AlarmOccurrenceOutboxKind.ARRIVED
                    } else {
                        AlarmOccurrenceOutboxKind.FORCE_ENDED
                    },
                    occurredAtEpochMillis = terminalAtEpochMillis,
                ))
            }
        }
        val movements = buildList {
            add(IosAlarmCleanupMovementProjection(
                deduplicationKey = "start",
                action = AlarmMovementAction.START_MOVEMENT,
                occurredAtEpochMillis = attempts.minBy { it.sequence }.stopAtEpochMillis,
            ))
            if (!unsupportedOverlap) add(IosAlarmCleanupMovementProjection(
                deduplicationKey = "terminal",
                action = if (terminalOutcome == IosAlarmRecoveryAttemptOutcome.TERMINAL_SUCCEEDED) {
                    AlarmMovementAction.ARRIVE
                } else {
                    AlarmMovementAction.GIVE_UP
                },
                occurredAtEpochMillis = terminalAtEpochMillis,
            ))
        }
        return IosAlarmCleanupReadinessRequest(
            chainId = chainId,
            rootOccurrenceId = rootOccurrenceId,
            alarmId = sourceAlarmId,
            scheduleVersion = scheduleVersion,
            expectedOutbox = outbox,
            expectedActivity = attempts.map { attempt ->
                val scheduledAt = attempt.scheduledAtEpochMillis
                    ?: attempt.snapshot.scheduledAtEpochMillis
                    ?: attempt.snapshot.originalTime?.let { time ->
                        iosInitialRingingScheduledAt(time, attempt.stopAtEpochMillis)
                    }
                IosAlarmCleanupActivityProjection(
                    occurrenceId = attempt.occurrenceId,
                    alarmId = sourceAlarmId,
                    scheduleVersion = scheduleVersion,
                    ringingScheduledAtEpochMillis = scheduledAt,
                    ringingStoppedAtEpochMillis = attempt.stopAtEpochMillis,
                    ringingStartObserved = false,
                )
            },
            expectedHistory = if (unsupportedOverlap) null else IosAlarmCleanupHistoryProjection(
                occurrenceId = terminalOccurrenceId,
                result = if (terminalOutcome == IosAlarmRecoveryAttemptOutcome.TERMINAL_SUCCEEDED) "SUCCESS" else "FAILURE",
                completedAtEpochMillis = terminalAtEpochMillis,
            ),
            expectedMovements = movements,
            requireAllOccurrenceRowsSent = true,
            requireAllMovementRowsSent = true,
        )
    }

    val protectedReferences: Set<String>
        get() = buildSet {
            add(chainId)
            add(rootOccurrenceId)
            attempts.forEach { attempt ->
                add(attempt.occurrenceId)
                add(attempt.nextOccurrenceToken)
            }
            add(terminalOccurrenceId)
        }
}

data class IosAlarmNativeCleanupCandidatesResult(
    val events: List<IosAlarmNativeCleanupEventCandidate> = emptyList(),
    val chains: List<IosAlarmNativeCleanupChainCandidate> = emptyList(),
    val code: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
    val message: String? = null,
)

data class IosAlarmNativeCleanupCommitResult(
    val committed: Boolean = false,
    val stale: Boolean = false,
    val code: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
    val message: String? = null,
)
