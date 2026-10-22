import Foundation
import Shared

extension AlarmRecoveryAttempt {
    func toMissionEventDto() -> IosAlarmMissionEventDto {
        IosAlarmMissionEventDto(
            eventId: occurrenceId,
            alarmId: snapshot.sourceAlarmId,
            occurrenceId: occurrenceId,
            action: .stop,
            occurredAtEpochMillis: stopAtEpochMillis,
            retryAttempt: Int32(sequence),
            ringingObservedAtEpochMillis: nil,
            ringingStoppedAtEpochMillis: KotlinLong(value: stopAtEpochMillis),
            scheduleVersion: snapshot.scheduleVersion,
            ownerAccountId: snapshot.owner.dtoAccountId,
            ownerCaptured: snapshot.owner.dtoCaptured,
            chainId: chainId
        )
    }

    func toRecoveryAttemptDto() -> IosAlarmRecoveryAttemptDto {
        IosAlarmRecoveryAttemptDto(
            occurrenceId: occurrenceId,
            chainId: chainId,
            parentOccurrenceId: parentOccurrenceId,
            sequence: Int32(sequence),
            snapshot: snapshot.toDto(),
            stopAtEpochMillis: stopAtEpochMillis,
            scheduledAtEpochMillis: scheduledAtEpochMillis.map { KotlinLong(value: $0) },
            expiresAtEpochMillis: expiresAtEpochMillis,
            nextReservationUuid: nextReservationUUID,
            nextOccurrenceToken: nextOccurrenceToken,
            currentReservation: currentReservation.toDto(),
            missionProjectionAck: missionProjectionAck,
            recordProjectionAck: recordProjectionAck,
            outcome: outcome.toDto()
        )
    }
}

extension AlarmRecoveryOwner {
    var dtoAccountId: String? {
        switch kind {
        case .capturedAccount:
            return accountId
        case .capturedGuest, .uncaptured:
            return nil
        }
    }

    var dtoCaptured: Bool {
        switch kind {
        case .capturedAccount, .capturedGuest:
            return true
        case .uncaptured:
            return false
        }
    }
}

extension AlarmRecoverySourceSnapshot {
    func toDto() -> IosAlarmRecoverySourceSnapshotDto {
        IosAlarmRecoverySourceSnapshotDto(
            sourceAlarmId: sourceAlarmId,
            scheduleVersion: scheduleVersion,
            destination: IosAlarmRecoveryDestinationSnapshotDto(
                name: destination.name,
                latitude: destination.latitude,
                longitude: destination.longitude
            ),
            limitSeconds: limitSeconds,
            scheduledAtEpochMillis: scheduledAtEpochMillis.map { KotlinLong(value: $0) },
            originalTime: originalTime,
            arrivalRadiusMeters: arrivalRadiusMeters.map { KotlinDouble(value: $0) },
            repeatRule: repeatRule,
            title: title,
            soundName: soundName,
            soundUri: soundURI,
            owner: owner.toDto()
        )
    }
}

extension AlarmRecoveryOwner {
    func toDto() -> IosAlarmRecoveryOwnerDto {
        IosAlarmRecoveryOwnerDto(kind: kind.toDto(), accountId: accountId)
    }
}

extension AlarmRecoveryOwner.Kind {
    func toDto() -> IosAlarmRecoveryOwnerKind {
        switch self {
        case .capturedAccount:
            return .capturedAccount
        case .capturedGuest:
            return .capturedGuest
        case .uncaptured:
            return .uncaptured
        }
    }
}

extension AlarmRecoveryReservationLedger {
    func toDto() -> IosAlarmRecoveryReservationDto {
        IosAlarmRecoveryReservationDto(
            reservationUuid: reservationUUID,
            status: status.toDto(),
            operationId: operationId,
            generation: generation,
            executionEpoch: executionEpoch,
            lastUpdatedEpochMillis: lastUpdatedEpochMillis,
            lastError: lastError,
            cancellationRequired: cancellationRequired,
            cancellationConfirmedAtEpochMillis: cancellationConfirmedAtEpochMillis.map { KotlinLong(value: $0) },
            lateScheduleAfterTerminal: lateScheduleAfterTerminal,
            scheduleCallSettled: scheduleCallSettled
        )
    }
}

extension AlarmRecoveryReservationStatus {
    func toDto() -> IosAlarmRecoveryReservationStatus {
        switch self {
        case .pending:
            return .pending
        case .inFlight:
            return .inFlight
        case .confirmed:
            return .confirmed
        case .failed:
            return .failed
        case .unknown:
            return .unknown
        case .absent:
            return .absent
        case .cancellationRequired:
            return .cancellationRequired
        case .cancellationConfirmed:
            return .cancellationConfirmed
        }
    }
}

extension AlarmRecoveryAttemptOutcome {
    func toDto() -> IosAlarmRecoveryAttemptOutcome {
        switch self {
        case .active:
            return .active
        case .expired:
            return .expired
        case .terminalSucceeded:
            return .terminalSucceeded
        case .terminalForcedStop:
            return .terminalForcedStop
        case .skippedUnsupportedOverlap:
            return .skippedUnsupportedOverlap
        }
    }
}

extension IosAlarmRecoverySourceSnapshotDto {
    func toRecoverySnapshot() -> AlarmRecoverySourceSnapshot {
        AlarmRecoverySourceSnapshot(
            sourceAlarmId: sourceAlarmId,
            scheduleVersion: scheduleVersion,
            destination: AlarmRecoveryDestinationSnapshot(
                name: destination.name,
                latitude: destination.latitude,
                longitude: destination.longitude
            ),
            limitSeconds: limitSeconds,
            scheduledAtEpochMillis: scheduledAtEpochMillis?.int64Value,
            originalTime: originalTime,
            arrivalRadiusMeters: arrivalRadiusMeters?.doubleValue,
            repeatRule: repeatRule,
            title: title,
            soundName: soundName,
            soundURI: soundUri,
            owner: owner.toRecoveryOwner()
        )
    }
}

extension IosAlarmRecoveryOwnerDto {
    func toRecoveryOwner() -> AlarmRecoveryOwner {
        switch kind {
        case .capturedAccount:
            return accountId.map(AlarmRecoveryOwner.capturedAccount) ?? .uncaptured
        case .capturedGuest:
            return .capturedGuest
        case .uncaptured:
            return .uncaptured
        default:
            return .uncaptured
        }
    }
}

extension IosAlarmRecoveryEligibility {
    func toRecoveryEligibility() -> AlarmRecoveryEligibility {
        switch self {
        case .enabled:
            return .enabled
        case .disabled:
            return .disabled
        case .deleted:
            return .deleted
        case .unknown:
            return .unknown
        default:
            return .unknown
        }
    }
}

extension IosAlarmRecoveryReservationStatus {
    func toRecoveryReservationStatus() -> AlarmRecoveryReservationStatus {
        switch self {
        case .pending:
            return .pending
        case .inFlight:
            return .inFlight
        case .confirmed:
            return .confirmed
        case .failed:
            return .failed
        case .unknown:
            return .unknown
        case .absent:
            return .absent
        case .cancellationRequired:
            return .cancellationRequired
        case .cancellationConfirmed:
            return .cancellationConfirmed
        default:
            return .unknown
        }
    }
}

extension IosAlarmRecoveryAttemptOutcome {
    func toRecoveryAttemptOutcome() -> AlarmRecoveryAttemptOutcome {
        switch self {
        case .active:
            return .active
        case .expired:
            return .expired
        case .terminalSucceeded:
            return .terminalSucceeded
        case .terminalForcedStop:
            return .terminalForcedStop
        case .skippedUnsupportedOverlap:
            return .skippedUnsupportedOverlap
        default:
            return .terminalForcedStop
        }
    }
}

extension AlarmRecoveryState {
    /// Convert attempts and terminals from one locked journal read.
    func toRecoveryResult() -> IosAlarmRecoveryAttemptsResult {
        let attempts = chains.values.flatMap(\.attempts)
            .sorted { ($0.chainId, $0.sequence) < ($1.chainId, $1.sequence) }
            .map { $0.toRecoveryAttemptDto() }
        let liveTerminals = chains.values.compactMap { chain -> IosAlarmRecoveryTerminalDto? in
            guard let outcome = chain.terminalOutcome, let at = chain.terminalAtEpochMillis,
                  let latest = chain.attempts.max(by: { $0.sequence < $1.sequence }) else { return nil }
            return IosAlarmRecoveryTerminalDto(
                chainId: chain.chainId, occurrenceId: chain.terminalOccurrenceId ?? latest.occurrenceId,
                outcome: outcome.toDto(), completedAtEpochMillis: at,
                terminalRecordAck: chain.terminalRecordAck
            )
        }
        let compactedTerminals = terminalTombstones.values.map { tombstone in
            IosAlarmRecoveryTerminalDto(
                chainId: tombstone.chainId, occurrenceId: tombstone.terminalOccurrenceId,
                outcome: tombstone.terminalOutcome.toDto(), completedAtEpochMillis: tombstone.terminalAtEpochMillis,
                terminalRecordAck: true
            )
        }
        return IosAlarmRecoveryAttemptsResult(attempts: attempts, terminals: liveTerminals + compactedTerminals, code: .success, message: nil)
    }
}
