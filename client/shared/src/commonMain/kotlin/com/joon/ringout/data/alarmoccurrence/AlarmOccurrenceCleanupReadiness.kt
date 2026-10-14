package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.domain.alarmmovement.AlarmMovementAction

/** A Room proof request derived from one immutable native cleanup candidate. */
data class IosAlarmCleanupReadinessRequest(
    val chainId: String,
    val rootOccurrenceId: String,
    val alarmId: String,
    val scheduleVersion: Long,
    val expectedOutbox: List<IosAlarmCleanupOutboxProjection> = emptyList(),
    val expectedActivity: List<IosAlarmCleanupActivityProjection> = emptyList(),
    val expectedHistory: IosAlarmCleanupHistoryProjection? = null,
    val expectedMovements: List<IosAlarmCleanupMovementProjection> = emptyList(),
    val requireAllOccurrenceRowsSent: Boolean = false,
    val requireAllMovementRowsSent: Boolean = false,
)

data class IosAlarmCleanupOutboxProjection(
    val occurrenceId: String,
    val deduplicationKey: String,
    val kind: AlarmOccurrenceOutboxKind,
    val occurredAtEpochMillis: Long?,
)

data class IosAlarmCleanupActivityProjection(
    val occurrenceId: String,
    val alarmId: String,
    val scheduleVersion: Long,
    val ringingScheduledAtEpochMillis: Long? = null,
    val ringingStartedAtEpochMillis: Long? = null,
    val ringingStoppedAtEpochMillis: Long? = null,
    val ringingStartObserved: Boolean? = null,
)

data class IosAlarmCleanupHistoryProjection(
    val occurrenceId: String,
    val result: String,
    val completedAtEpochMillis: Long,
)

data class IosAlarmCleanupMovementProjection(
    val deduplicationKey: String,
    val action: AlarmMovementAction,
    val occurredAtEpochMillis: Long,
)

enum class IosAlarmCleanupReadinessReason {
    READY,
    AUTH_PENDING,
    MISSING_DECISION,
    MISSING_PROJECTION,
    MISSING_OUTBOX,
    PENDING,
    BLOCKED,
}

data class IosAlarmCleanupReadiness(
    val reason: IosAlarmCleanupReadinessReason,
    val ownerAccountId: String? = null,
) {
    val isReady: Boolean get() = reason == IosAlarmCleanupReadinessReason.READY
}
