package com.joon.ringout.alarm

data class IosAlarmRecoveryDestinationSnapshotDto(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

enum class IosAlarmRecoveryOwnerKind {
    CAPTURED_ACCOUNT,
    CAPTURED_GUEST,
    UNCAPTURED,
}

data class IosAlarmRecoveryOwnerDto(
    val kind: IosAlarmRecoveryOwnerKind,
    val accountId: String? = null,
)

data class IosAlarmRecoverySourceSnapshotDto(
    val sourceAlarmId: String,
    val scheduleVersion: Long,
    val destination: IosAlarmRecoveryDestinationSnapshotDto,
    val limitSeconds: Long,
    val scheduledAtEpochMillis: Long? = null,
    val originalTime: String? = null,
    val arrivalRadiusMeters: Double? = null,
    val repeatRule: String? = null,
    val title: String? = null,
    val soundName: String? = null,
    val soundUri: String? = null,
    val owner: IosAlarmRecoveryOwnerDto,
)

enum class IosAlarmRecoveryEligibility {
    ENABLED,
    DISABLED,
    DELETED,
    UNKNOWN,
}

enum class IosAlarmRecoveryReservationStatus {
    PENDING,
    IN_FLIGHT,
    CONFIRMED,
    FAILED,
    UNKNOWN,
    ABSENT,
    CANCELLATION_REQUIRED,
    CANCELLATION_CONFIRMED,
}

enum class IosAlarmRecoveryAttemptOutcome {
    ACTIVE,
    EXPIRED,
    TERMINAL_SUCCEEDED,
    TERMINAL_FORCED_STOP,
    SKIPPED_UNSUPPORTED_OVERLAP,
}

data class IosAlarmRecoveryReservationDto(
    val reservationUuid: String,
    val status: IosAlarmRecoveryReservationStatus,
    val operationId: String? = null,
    val generation: Long = 0,
    val executionEpoch: String? = null,
    val lastUpdatedEpochMillis: Long = 0,
    val lastError: String? = null,
    val cancellationRequired: Boolean = false,
    val cancellationConfirmedAtEpochMillis: Long? = null,
    val lateScheduleAfterTerminal: Boolean = false,
)

data class IosAlarmRecoveryAttemptDto(
    val occurrenceId: String,
    val chainId: String,
    val parentOccurrenceId: String? = null,
    val sequence: Int,
    val snapshot: IosAlarmRecoverySourceSnapshotDto,
    val stopAtEpochMillis: Long,
    val scheduledAtEpochMillis: Long? = null,
    val expiresAtEpochMillis: Long,
    val nextReservationUuid: String,
    val nextOccurrenceToken: String,
    val currentReservation: IosAlarmRecoveryReservationDto,
    val missionProjectionAck: Boolean,
    val recordProjectionAck: Boolean,
    val outcome: IosAlarmRecoveryAttemptOutcome,
)

data class IosAlarmRecoveryAttemptsResult(
    val attempts: List<IosAlarmRecoveryAttemptDto> = emptyList(),
    val terminals: List<IosAlarmRecoveryTerminalDto> = emptyList(),
    val code: IosAlarmOperationCode = IosAlarmOperationCode.SUCCESS,
    val message: String? = null,
)

data class IosAlarmRecoveryTerminalDto(
    val chainId: String,
    val occurrenceId: String,
    val outcome: IosAlarmRecoveryAttemptOutcome,
    val completedAtEpochMillis: Long,
    val terminalRecordAck: Boolean,
)
