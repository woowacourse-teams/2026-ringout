import Foundation

enum AlarmRecoveryStopDisposition {
    case handled
    case fallback
    case preserved
}

enum AlarmRecoveryDismissDisposition: Equatable {
    case handled
    case fallback
    case preserved
    case failed

    init(_ stop: AlarmRecoveryStopDisposition) {
        switch stop {
        case .handled: self = .handled
        case .fallback: self = .fallback
        case .preserved: self = .preserved
        }
    }
}

enum AlarmRecoveryOSStopCode {
    case success
    case notFound
    case invalidId
    case failed
    case notAttempted
}

struct AlarmRecoveryStopObservation {
    let occurrenceId: String
    let ownerCaptured: Bool
    let ownerAccountId: String?
}

struct AlarmRecoveryDismissOutcome {
    let osStopCode: AlarmRecoveryOSStopCode
    let disposition: AlarmRecoveryDismissDisposition
    let state: AlarmRecoveryState?
    let occurrenceId: String?
    let message: String?
}

/// The shared durable algorithm for Intent and in-app stops. Only OS operations
/// and ringing evidence are injected; the journal remains the scheduling authority.
final class AlarmRecoveryCommand {
    let journal: AlarmRecoveryJournal
    private let executionEpoch: String
    private let clock: () -> Date
    private let stopAlarm: (String) async throws -> AlarmRecoveryOSStopCode
    private let schedule: (AlarmRecoveryAttempt) async throws -> Void
    private let contains: (String) async throws -> Bool
    private let cancel: (String) async throws -> Void
    private let observation: (String, Date) -> AlarmRecoveryStopObservation?

    init(
        journal: AlarmRecoveryJournal,
        executionEpoch: String = UUID().uuidString,
        clock: @escaping () -> Date = Date.init,
        stopAlarm: @escaping (String) async throws -> AlarmRecoveryOSStopCode,
        schedule: @escaping (AlarmRecoveryAttempt) async throws -> Void,
        contains: @escaping (String) async throws -> Bool,
        cancel: @escaping (String) async throws -> Void,
        observation: @escaping (String, Date) -> AlarmRecoveryStopObservation? = { _, _ in nil }
    ) {
        self.journal = journal
        self.executionEpoch = executionEpoch
        self.clock = clock
        self.stopAlarm = stopAlarm
        self.schedule = schedule
        self.contains = contains
        self.cancel = cancel
        self.observation = observation
    }

    func dismiss(
        alarmId: String,
        occurrenceToken: String?,
        retryAttempt: Int,
        scheduleVersion: Int64,
        systemAlarmId: String,
        chainId: String? = nil,
        now: Date = Date()
    ) async -> AlarmRecoveryDismissOutcome {
        var osStopCode: AlarmRecoveryOSStopCode = .notAttempted
        do {
            let before = try journal.recoveryState()
            let key = AlarmRecoverySourceKey(sourceAlarmId: alarmId, scheduleVersion: scheduleVersion)
            let exactAttempt = Self.exactAttempt(in: before, token: occurrenceToken, key: key)
            let route = try journal.routeStop(occurrenceToken: occurrenceToken, retryAttempt: retryAttempt,
                                             sourceKey: key, stopAt: now)
            // Preflight before silencing a new source: a missing snapshot must
            // surface as a failure, never produce a legacy inbox event.
            if exactAttempt == nil, case .newRoot = route, (before.snapshots[key] == nil || before.sources[key] != .enabled) {
                return AlarmRecoveryDismissOutcome(osStopCode: osStopCode, disposition: .failed,
                    state: before, occurrenceId: nil, message: "Missing alarm recovery source snapshot")
            }
            if let exactAttempt, let chainId, exactAttempt.chainId != chainId {
                return AlarmRecoveryDismissOutcome(osStopCode: osStopCode, disposition: .preserved,
                    state: before, occurrenceId: nil, message: "Recovery chain does not match")
            }
            osStopCode = .failed
            osStopCode = try await stopAlarm(systemAlarmId)
            if osStopCode == .failed || osStopCode == .invalidId {
                return AlarmRecoveryDismissOutcome(osStopCode: osStopCode, disposition: .failed,
                    state: try journal.recoveryState(), occurrenceId: nil, message: "AlarmKit stop failed")
            }
            let afterStopRoute = try journal.routeStop(occurrenceToken: occurrenceToken, retryAttempt: retryAttempt,
                                                      sourceKey: key, stopAt: now)
            let afterStop = try journal.recoveryState()
            let existing: AlarmRecoveryAttempt?
            if let exact = Self.exactAttempt(in: afterStop, token: occurrenceToken, key: key) {
                existing = exact
            } else if occurrenceToken == nil, retryAttempt == 0,
                      case let .liveAttempt(root) = afterStopRoute, root.parentOccurrenceId == nil {
                // The journal's bounded root coalescing window is the existing
                // tokenless replay contract; an unknown retry never uses it.
                existing = root
            } else { existing = nil }
            if let existing, chainId == nil || chainId == existing.chainId {
                // A process may have committed the attempt before reaching its
                // reservation claim. Resume PENDING through the same CAS gate;
                // IN_FLIGHT, UNKNOWN and settled reservations cannot resubmit.
                try await submit(existing)
                let resumedRoute = try journal.routeStop(occurrenceToken: occurrenceToken, retryAttempt: retryAttempt,
                                                        sourceKey: key, stopAt: now)
                let resumedState = try journal.recoveryState()
                let appliedOccurrenceId: String
                if case let .compactedTerminal(tombstone) = resumedRoute {
                    appliedOccurrenceId = tombstone.terminalOccurrenceId
                } else { appliedOccurrenceId = existing.occurrenceId }
                return AlarmRecoveryDismissOutcome(osStopCode: osStopCode, disposition: .handled,
                    state: resumedState, occurrenceId: appliedOccurrenceId, message: nil)
            }
            if osStopCode == .notFound {
                if case let .compactedTerminal(tombstone) = afterStopRoute {
                    return AlarmRecoveryDismissOutcome(osStopCode: osStopCode, disposition: .handled,
                        state: afterStop, occurrenceId: tombstone.terminalOccurrenceId, message: nil)
                }
                return AlarmRecoveryDismissOutcome(osStopCode: osStopCode, disposition: .preserved,
                    state: afterStop, occurrenceId: nil, message: "Ringing alarm was not found")
            }
            let disposition = try await stop(alarmId: alarmId, occurrenceToken: occurrenceToken,
                retryAttempt: retryAttempt, scheduleVersion: scheduleVersion, systemAlarmId: systemAlarmId, now: now)
            let latestRoute = try journal.routeStop(occurrenceToken: occurrenceToken, retryAttempt: retryAttempt,
                                                   sourceKey: key, stopAt: now)
            let latest = try journal.recoveryState()
            let accepted: AlarmRecoveryAttempt?
            if let exact = Self.exactAttempt(in: latest, token: occurrenceToken, key: key) {
                accepted = exact
            } else if occurrenceToken == nil, case let .liveAttempt(attempt) = latestRoute {
                accepted = attempt
            } else {
                accepted = nil
            }
            let appliedOccurrenceId: String?
            if case let .compactedTerminal(tombstone) = latestRoute {
                appliedOccurrenceId = tombstone.terminalOccurrenceId
            } else { appliedOccurrenceId = accepted?.occurrenceId }
            return AlarmRecoveryDismissOutcome(osStopCode: osStopCode, disposition: AlarmRecoveryDismissDisposition(disposition),
                state: latest, occurrenceId: appliedOccurrenceId, message: nil)
        } catch {
            return AlarmRecoveryDismissOutcome(osStopCode: osStopCode, disposition: .failed,
                state: try? journal.recoveryState(), occurrenceId: nil, message: error.localizedDescription)
        }
    }

    private static func exactAttempt(
        in state: AlarmRecoveryState, token: String?, key: AlarmRecoverySourceKey
    ) -> AlarmRecoveryAttempt? {
        guard let token else { return nil }
        return state.chains.values.flatMap(\.attempts).first {
            $0.sourceKey == key && ($0.occurrenceId == token || state.tokenIndex["token|\(token)"] == $0.occurrenceId)
        }
    }

    @discardableResult
    func stop(
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
                try await cancelTombstone(reservationId)
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
        let observation = parent == nil ? observation(systemAlarmId, now) : nil
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

    func recover() async throws {
        let attempts = try journal.attemptsForRecovery()
        var lastError: Error?
        for attempt in attempts {
            do {
            if attempt.currentReservation.cancellationRequired {
                try await cancelReservation(attempt)
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
                try await cancelTombstone(reservationUUID)
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

    private func submit(_ attempt: AlarmRecoveryAttempt) async throws {
        guard let claim = try journal.claimNextReservation(
            occurrenceId: attempt.occurrenceId, executionEpoch: executionEpoch, now: clock()
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
            try await cancelReservation(updated)
        }
    }

    private func cancelReservation(_ attempt: AlarmRecoveryAttempt) async throws {
        if try await contains(attempt.nextReservationUUID) {
            try await cancel(attempt.nextReservationUUID)
        }
        try journal.markCancellationConfirmed(reservationUUID: attempt.nextReservationUUID)
    }

    private func cancelTombstone(_ rawId: String) async throws {
        if try await contains(rawId) { try await cancel(rawId) }
        try journal.markTombstoneCancellationConfirmed(reservationUUID: rawId)
    }
}
