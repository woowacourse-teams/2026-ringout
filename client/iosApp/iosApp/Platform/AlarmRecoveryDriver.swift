import AlarmKit
import Foundation
import SwiftUI

private struct RecoveryAlarmMetadata: AlarmMetadata {
    let sourceAlarmId: String
    let occurrenceToken: String
}

enum AlarmRecoveryStopDisposition {
    case handled
    case fallback
    case preserved
}

/// The only scheduler for journal-backed retries. No Room, DI or foreground
/// construction is needed between a stop and its durable next reservation.
enum AlarmRecoveryDriver {
    static let journal = AlarmRecoveryJournal()
    private static let executionEpoch = UUID().uuidString

    @discardableResult
    static func stop(
        alarmId: String,
        occurrenceToken: String?,
        retryAttempt: Int = 0,
        scheduleVersion: Int64,
        systemAlarmId: String,
        now: Date = Date()
    ) async throws -> AlarmRecoveryStopDisposition {
        let state = try journal.recoveryState()
        let sourceKey = AlarmRecoverySourceKey(sourceAlarmId: alarmId, scheduleVersion: scheduleVersion)
        let route = try journal.routeStop(
            occurrenceToken: occurrenceToken,
            retryAttempt: retryAttempt,
            sourceKey: sourceKey,
            stopAt: now
        )
        if case let .compactedTerminal(tombstone) = route {
            let reservationId = UUID(uuidString: systemAlarmId)?.uuidString
            let expectedReservationId = occurrenceToken.flatMap {
                tombstone.retryReservationUUIDByToken[$0]
            }
            if let reservationId, reservationId == expectedReservationId {
                try journal.requireTombstoneCancellation(reservationUUID: reservationId)
                try await cancelTombstoneReservation(reservationId)
            }
            return .handled
        }
        if case .unknownRetry = route {
            return Self.hasLegacyRetryProof(
                alarmId: alarmId,
                occurrenceToken: occurrenceToken,
                retryAttempt: retryAttempt,
                scheduleVersion: scheduleVersion,
                systemAlarmId: systemAlarmId
            ) ? .fallback : .preserved
        }

        let parent: AlarmRecoveryAttempt?
        if let occurrenceToken, case let .liveAttempt(attempt) = route,
           attempt.nextOccurrenceToken == occurrenceToken {
            parent = attempt
        } else {
            parent = nil
        }
        if let parent, state.chains[parent.chainId]?.terminalOutcome != nil {
            try await recover()
            return .handled
        }
        guard var snapshot = try parent?.snapshot ?? journal.sourceSnapshot(
            sourceAlarmId: alarmId, scheduleVersion: scheduleVersion
        ) else {
            return retryAttempt == 0 ? .fallback : .preserved
        }
        let observation = parent == nil ? try? AlarmRingingOccurrenceStore.shared.observation(
            systemAlarmId: systemAlarmId, now: now
        ) : nil
        if let observation, observation.ownerCaptured == true {
            snapshot = AlarmRecoverySourceSnapshot(
                sourceAlarmId: snapshot.sourceAlarmId,
                scheduleVersion: snapshot.scheduleVersion,
                destination: snapshot.destination,
                limitSeconds: snapshot.limitSeconds,
                scheduledAtEpochMillis: snapshot.scheduledAtEpochMillis,
                originalTime: snapshot.originalTime,
                arrivalRadiusMeters: snapshot.arrivalRadiusMeters,
                repeatRule: snapshot.repeatRule,
                title: snapshot.title,
                soundName: snapshot.soundName,
                soundURI: snapshot.soundURI,
                owner: observation.ownerAccountId.map(AlarmRecoveryOwner.capturedAccount) ?? .capturedGuest
            )
        }
        let accepted: AlarmRecoveryAcceptedStop
        do {
            let acceptedRetryAttempt: Int
            if let parent, retryAttempt == 0 {
                acceptedRetryAttempt = parent.sequence + 1
            } else {
                acceptedRetryAttempt = retryAttempt
            }
            accepted = try journal.acceptStop(
                snapshot: snapshot,
                occurrenceToken: occurrenceToken ?? observation?.occurrenceId,
                retryAttempt: acceptedRetryAttempt,
                parentOccurrenceId: parent?.occurrenceId,
                rootOccurrenceId: parent == nil ? observation?.occurrenceId : nil,
                stopAt: now
            )
        } catch AlarmRecoveryJournalError.sourceNotEligible {
            return .preserved
        } catch AlarmRecoveryJournalError.terminalChain {
            // Another process may have compacted or terminated the chain after
            // the route lookup. The retry is consumed without creating a root.
            return .handled
        } catch AlarmRecoveryJournalError.missingParentAttempt {
            // A concurrently removed parent is ambiguous. Keep the event out of
            // the legacy/root fallback and let the next recovery read decide.
            return .preserved
        }
        // Persist before any actor hop, OS request or foreground authentication.
        try await submit(accepted.attempt)
        return .handled
    }

    static func recover() async throws {
        let attempts = try journal.attemptsForRecovery()
        var lastError: Error?
        for attempt in attempts {
            do {
            if attempt.currentReservation.cancellationRequired {
                try await cancel(attempt)
            } else if attempt.currentReservation.status == .pending {
                try await submit(attempt)
            } else if attempt.currentReservation.status != .cancellationConfirmed {
                // Absence is not evidence that an abandoned daemon call failed.
                let present = try await contains(attempt.nextReservationUUID)
                try journal.recordReservationPresence(
                    reservationUUID: attempt.nextReservationUUID,
                    presence: present ? .confirmed : .absent
                )
            }
            } catch { lastError = error }
        }
        for reservationUUID in try journal.compactedReservationUUIDs() {
            if try await contains(reservationUUID) {
                try journal.requireTombstoneCancellation(reservationUUID: reservationUUID)
            }
        }
        for reservationUUID in try journal.tombstoneCancellationRequiredUUIDs() {
            do {
                try await cancelTombstoneReservation(reservationUUID)
            } catch { lastError = error }
        }
        if let lastError { throw lastError }
    }

    private static func hasLegacyRetryProof(
        alarmId: String,
        occurrenceToken: String?,
        retryAttempt: Int,
        scheduleVersion: Int64,
        systemAlarmId: String
    ) -> Bool {
        guard retryAttempt > 0,
              let occurrenceToken,
              let active = UserDefaults.standard.stringArray(forKey: "ios.activeMission.record.v2"),
              active.count >= 13,
              let activeSequence = Int(active[12]),
              let deadline = UserDefaults.standard.stringArray(forKey: "ios.activeMission.deadlineAlarm.v1"),
              deadline.count >= 3 else { return false }
        let activeScheduleVersion = active.count > 13 ? Int64(active[13]) ?? 1 : 1
        return (UUID(uuidString: active[0])?.uuidString ?? active[0]) ==
                (UUID(uuidString: alarmId)?.uuidString ?? alarmId) &&
            activeScheduleVersion == scheduleVersion &&
            retryAttempt == activeSequence + 1 &&
            deadline[1] == active[1] &&
            deadline[2] == occurrenceToken &&
            (UUID(uuidString: deadline[0])?.uuidString ?? deadline[0]) ==
                (UUID(uuidString: systemAlarmId)?.uuidString ?? systemAlarmId)
    }

    private static func submit(_ attempt: AlarmRecoveryAttempt) async throws {
        guard let claim = try journal.claimNextReservation(
            occurrenceId: attempt.occurrenceId, executionEpoch: executionEpoch
        ) else { return }
        do {
            try await schedule(attempt)
        } catch {
            // The awaited API returned a failure; preserve it independently of
            // mission projection. Never generate a replacement reservation.
            try journal.completeReservation(
                claim: claim, status: .failed,
                error: "\((error as NSError).domain):\((error as NSError).code)"
            )
            return
        }
        try journal.completeReservation(claim: claim, status: .confirmed)
        if let updated = try journal.attemptsForRecovery().first(where: {
            $0.occurrenceId == attempt.occurrenceId
        }), updated.currentReservation.cancellationRequired {
            try await cancel(updated)
        }
    }

    @MainActor
    private static func schedule(_ attempt: AlarmRecoveryAttempt) async throws {
        guard let id = UUID(uuidString: attempt.nextReservationUUID) else {
            throw AlarmMissionEventInboxError.invalidAlarmId
        }
        let title = attempt.snapshot.title ?? attempt.snapshot.destination.name
        let alert: AlarmPresentation.Alert
        if #available(iOS 26.1, *) {
            alert = AlarmPresentation.Alert(title: LocalizedStringResource(String.LocalizationValue(title)))
        } else {
            alert = AlarmPresentation.Alert(
                title: LocalizedStringResource(String.LocalizationValue(title)),
                stopButton: AlarmButton(text: "중지", textColor: .white, systemImageName: "stop.fill")
            )
        }
        let attributes = AlarmAttributes(
            presentation: AlarmPresentation(alert: alert),
            metadata: RecoveryAlarmMetadata(
                sourceAlarmId: attempt.snapshot.sourceAlarmId,
                occurrenceToken: attempt.nextOccurrenceToken
            ),
            tintColor: .orange
        )
        let configuration = AlarmManager.AlarmConfiguration.alarm(
            schedule: .fixed(Date(timeIntervalSince1970: Double(attempt.expiresAtEpochMillis) / 1_000)),
            attributes: attributes,
            stopIntent: StopAlarmIntent(
                alarmId: attempt.snapshot.sourceAlarmId,
                occurrenceId: attempt.nextOccurrenceToken,
                retryAttempt: attempt.sequence + 1,
                systemAlarmId: attempt.nextReservationUUID,
                scheduleVersion: Int(attempt.snapshot.scheduleVersion)
            ),
            sound: .default
        )
        _ = try await AlarmManager.shared.schedule(id: id, configuration: configuration)
    }

    @MainActor
    private static func contains(_ rawId: String) throws -> Bool {
        guard let id = UUID(uuidString: rawId) else { return false }
        return try AlarmManager.shared.alarms.contains { $0.id == id }
    }

    @MainActor
    private static func cancel(_ attempt: AlarmRecoveryAttempt) throws {
        guard let id = UUID(uuidString: attempt.nextReservationUUID) else { return }
        if try AlarmManager.shared.alarms.contains(where: { $0.id == id }) {
            try AlarmManager.shared.cancel(id: id)
        }
        // In-flight/unknown owners may still produce a late schedule. Their
        // obligation remains protected even after a currently absent snapshot.
        try journal.markCancellationConfirmed(reservationUUID: attempt.nextReservationUUID)
    }

    @MainActor
    private static func cancelTombstoneReservation(_ rawId: String) throws {
        guard let id = UUID(uuidString: rawId) else { return }
        if try AlarmManager.shared.alarms.contains(where: { $0.id == id }) {
            try AlarmManager.shared.cancel(id: id)
        }
        try journal.markTombstoneCancellationConfirmed(reservationUUID: id.uuidString)
    }
}
