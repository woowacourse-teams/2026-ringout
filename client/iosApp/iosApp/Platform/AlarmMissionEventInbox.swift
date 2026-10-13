import CryptoKit
import Darwin
import Foundation
import Shared

struct AlarmMissionEvent: Codable, Equatable {
    let eventId: String
    let alarmId: String
    let occurrenceId: String
    let action: AlarmMissionEventAction
    let occurredAtEpochMillis: Int64
    let retryAttempt: Int?
    let scheduleVersion: Int64?
    let source: AlarmMissionEventSource?
    let ringingObservedAtEpochMillis: Int64?
    let ringingStoppedAtEpochMillis: Int64?
    let ownerAccountId: String?
    let ownerCaptured: Bool?
    let chainId: String?
    var consumedAtEpochMillis: Int64?
    var recordProjectionAckAtEpochMillis: Int64? = nil

    var isConsumed: Bool {
        consumedAtEpochMillis != nil
    }

    var requiresRecordProjection: Bool {
        action == .stop || ringingObservedAtEpochMillis != nil || ringingStoppedAtEpochMillis != nil
    }
}

private struct AlarmMissionEventDedupRecord: Codable, Equatable {
    let eventId: String
    let alarmId: String
    let occurrenceId: String
    let action: AlarmMissionEventAction
    let occurredAtEpochMillis: Int64
    let retryAttempt: Int?
    let scheduleVersion: Int64?
    let source: AlarmMissionEventSource?
    let ownerAccountId: String?
    let ownerCaptured: Bool?
    let chainId: String?

    init(event: AlarmMissionEvent) {
        eventId = event.eventId
        alarmId = event.alarmId
        occurrenceId = event.occurrenceId
        action = event.action
        occurredAtEpochMillis = event.occurredAtEpochMillis
        retryAttempt = event.retryAttempt
        scheduleVersion = event.scheduleVersion
        source = event.source
        ownerAccountId = event.ownerAccountId
        ownerCaptured = event.ownerCaptured
        chainId = event.chainId
    }
}

private struct AlarmMissionEventInboxState: Codable {
    static let currentSchemaVersion = 1

    let schemaVersion: Int
    var events: [AlarmMissionEvent]
    var dedupRecords: [AlarmMissionEventDedupRecord]

    static let empty = AlarmMissionEventInboxState(
        schemaVersion: currentSchemaVersion,
        events: [],
        dedupRecords: []
    )
}

enum AlarmMissionEventAction: String, Codable {
    case stop
    case open

    var iosAction: IosAlarmMissionAction {
        switch self {
        case .stop:
            return .stop
        case .open:
            return .openApp
        }
    }
}

enum AlarmMissionEventSource: String, Codable {
    case nativeIntent
    case runtimeFallback
    case runtimeCustom
}

final class RingoutAlarmMissionEventInbox: IosAlarmMissionEventInbox {
    static let shared = RingoutAlarmMissionEventInbox()

    private let fileManager: FileManager
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder
    private let lock = NSLock()
    private var listener: IosAlarmMissionEventListener?

    private init(fileManager: FileManager = .default) {
        self.fileManager = fileManager
        encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        decoder = JSONDecoder()
    }

    func setEventListener(listener: IosAlarmMissionEventListener?) {
        lock.withLock {
            self.listener = listener
        }
    }

    @discardableResult
    func record(
        alarmId rawAlarmId: String,
        action: AlarmMissionEventAction,
        occurrenceId requestedOccurrenceId: String? = nil,
        retryAttempt: Int = 0,
        scheduleVersion: Int64 = 1,
        chainId requestedChainId: String? = nil,
        source: AlarmMissionEventSource = .nativeIntent,
        systemAlarmId: String? = nil,
        didStopRinging: Bool = true,
        now: Date = Date()
    ) throws -> AlarmMissionEvent {
        guard let alarmUUID = UUID(uuidString: rawAlarmId) else {
            throw AlarmMissionEventInboxError.invalidAlarmId
        }

        let alarmId = alarmUUID.uuidString
        let occurredAtEpochMillis = Int64(now.timeIntervalSince1970 * 1_000)
        let event = try mutateEvents { events, dedupRecords in
            let observation = try? AlarmRingingOccurrenceStore.shared.consume(
                systemAlarmId: systemAlarmId.flatMap { UUID(uuidString: $0)?.uuidString } ?? alarmId,
                requestedOccurrenceId: requestedOccurrenceId,
                now: now
            )
            let recentOccurrenceId = dedupRecords.reversed().first { existing in
                let existingRetryAttempt = existing.retryAttempt ?? 0
                let elapsedMillis = occurredAtEpochMillis - existing.occurredAtEpochMillis
                let isFallbackDeliveryPair =
                    (source == .runtimeFallback && existing.source == .nativeIntent) ||
                    (source == .nativeIntent && existing.source == .runtimeFallback) ||
                    (source == .runtimeFallback && existing.source == .runtimeFallback)
                return existing.alarmId == alarmId &&
                    existingRetryAttempt == retryAttempt &&
                    (existing.scheduleVersion ?? 1) == scheduleVersion &&
                    isFallbackDeliveryPair &&
                    elapsedMillis >= 0 &&
                    elapsedMillis <= Self.occurrenceCoalescingWindowMillis
            }?.occurrenceId
            let occurrenceId = requestedOccurrenceId ?? recentOccurrenceId ?? observation?.occurrenceId ??
                "\(alarmId):\(UUID().uuidString)"
            let previous = events.first { $0.occurrenceId == occurrenceId }
            let previousDedupRecord = dedupRecords.first { $0.occurrenceId == occurrenceId }
            let previousChainId = dedupRecords.reversed().first { existing in
                existing.alarmId == alarmId &&
                    (existing.scheduleVersion ?? 1) == scheduleVersion &&
                    (existing.retryAttempt ?? 0) < retryAttempt
            }.flatMap { $0.chainId ?? (($0.retryAttempt ?? 0) == 0 ? $0.occurrenceId : nil) }
            let chainId = requestedChainId ?? previous?.chainId ??
                (retryAttempt == 0 ? occurrenceId : previousChainId)
            // Preserve an explicitly captured guest, including across native/fallback duplicates.
            let owner: IosAlarmOccurrenceOwner
            if let previous {
                owner = IosAlarmOccurrenceOwner(accountId: previous.ownerAccountId, captured: previous.ownerCaptured ?? false)
            } else if let previousDedupRecord {
                owner = IosAlarmOccurrenceOwner(
                    accountId: previousDedupRecord.ownerAccountId,
                    captured: previousDedupRecord.ownerCaptured ?? false
                )
            } else if let observation {
                owner = IosAlarmOccurrenceOwner(accountId: observation.ownerAccountId, captured: observation.ownerCaptured ?? false)
            } else {
                owner = IosAlarmOccurrenceAccount.shared.capture()
            }
            let event = AlarmMissionEvent(
                eventId: UUID().uuidString,
                alarmId: alarmId,
                occurrenceId: occurrenceId,
                action: action,
                occurredAtEpochMillis: occurredAtEpochMillis,
                retryAttempt: retryAttempt,
                scheduleVersion: scheduleVersion,
                source: source,
                ringingObservedAtEpochMillis: observation?.observedAtEpochMillis,
                ringingStoppedAtEpochMillis: source == .runtimeFallback || !didStopRinging ? nil : occurredAtEpochMillis,
                ownerAccountId: owner.accountId,
                ownerCaptured: owner.captured,
                chainId: chainId,
                consumedAtEpochMillis: nil
            )
            events.append(event)
            return event
        }
        notifyEventRecorded()
        return event
    }

    func recordOpenEvent(
        alarmId: String,
        occurrenceId: String?,
        retryAttempt: Int32,
        scheduleVersion: Int64,
        chainId: String?,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        do {
            try record(
                alarmId: alarmId,
                action: .open,
                occurrenceId: occurrenceId,
                retryAttempt: Int(retryAttempt),
                scheduleVersion: scheduleVersion,
                chainId: chainId,
                source: .runtimeCustom
            )
            callback(IosAlarmOperationResult(code: .success, message: nil))
        } catch {
            callback(
                IosAlarmOperationResult(
                    code: .sdkError,
                    message: error.localizedDescription
                )
            )
        }
    }

    func recordStopEvent(
        alarmId: String,
        occurrenceId: String?,
        retryAttempt: Int32,
        scheduleVersion: Int64,
        chainId: String?,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        Task {
            do {
                let disposition = try await AlarmRecoveryDriver.stop(
                    alarmId: alarmId,
                    occurrenceToken: occurrenceId,
                    retryAttempt: Int(retryAttempt),
                    scheduleVersion: scheduleVersion,
                    systemAlarmId: alarmId
                )
                switch disposition {
                case .handled, .preserved:
                    completeOnMain {
                        self.notifyEventRecorded()
                        callback(IosAlarmOperationResult(code: .success, message: nil))
                    }
                    return
                case .fallback:
                    break
                }
                try record(
                    alarmId: alarmId,
                    action: .stop,
                    occurrenceId: occurrenceId,
                    retryAttempt: Int(retryAttempt),
                    scheduleVersion: scheduleVersion,
                    chainId: chainId,
                    source: .runtimeFallback
                )
                completeOnMain {
                    callback(IosAlarmOperationResult(code: .success, message: nil))
                }
            } catch {
                completeOnMain {
                    callback(
                        IosAlarmOperationResult(
                            code: .sdkError,
                            message: error.localizedDescription
                        )
                    )
                }
            }
        }
    }

    func pendingEvents(callback: @escaping (IosAlarmMissionEventsResult) -> Void) {
        do {
            callback(
                IosAlarmMissionEventsResult(
                    events: try pendingEventDtos(),
                    code: .success,
                    message: nil
                )
            )
        } catch {
            callback(
                IosAlarmMissionEventsResult(
                    events: [],
                    code: .sdkError,
                    message: error.localizedDescription
                )
            )
        }
    }

    func recordProjectionEvents(callback: @escaping (IosAlarmMissionEventsResult) -> Void) {
        do {
            callback(
                IosAlarmMissionEventsResult(
                    events: try recordProjectionEventDtos(),
                    code: .success,
                    message: nil
                )
            )
        } catch {
            callback(
                IosAlarmMissionEventsResult(
                    events: [],
                    code: .sdkError,
                    message: error.localizedDescription
                )
            )
        }
    }

    func nativeCleanupCandidates(callback: @escaping (IosAlarmNativeCleanupCandidatesResult) -> Void) {
        do {
            let events = try withInboxFileLock { try readStateUnlocked().events }
                .map { event in
                    IosAlarmNativeCleanupEventCandidate(
                        eventId: event.eventId,
                        fingerprint: try eventFingerprint(event),
                        alarmId: event.alarmId,
                        occurrenceId: event.occurrenceId,
                        action: event.action.iosAction,
                        occurredAtEpochMillis: event.occurredAtEpochMillis,
                        retryAttempt: Int32(event.retryAttempt ?? 0),
                        ringingObservedAtEpochMillis: event.ringingObservedAtEpochMillis.map { KotlinLong(value: $0) },
                        ringingStoppedAtEpochMillis: event.ringingStoppedAtEpochMillis.map { KotlinLong(value: $0) },
                        scheduleVersion: event.scheduleVersion ?? 1,
                        ownerAccountId: event.ownerAccountId,
                        ownerCaptured: event.ownerCaptured ?? false,
                        chainId: event.chainId,
                        consumedAtEpochMillis: event.consumedAtEpochMillis.map { KotlinLong(value: $0) },
                        recordProjectionAckAtEpochMillis: event.recordProjectionAckAtEpochMillis.map { KotlinLong(value: $0) }
                    )
                }
            let chains: [IosAlarmNativeCleanupChainCandidate] = try AlarmRecoveryDriver.journal.terminalCleanupSnapshots()
                .compactMap { snapshot -> IosAlarmNativeCleanupChainCandidate? in
                    let chain = snapshot.chain
                    guard let outcome = chain.terminalOutcome,
                          let completedAt = chain.terminalAtEpochMillis,
                          let terminalOccurrenceId = chain.terminalOccurrenceId,
                          let root = chain.attempts.first(where: { $0.sequence == 0 }) else {
                        return nil
                    }
                    return IosAlarmNativeCleanupChainCandidate(
                        chainId: chain.chainId,
                        fingerprint: snapshot.fingerprint,
                        sourceAlarmId: root.snapshot.sourceAlarmId,
                        scheduleVersion: root.snapshot.scheduleVersion,
                        rootOccurrenceId: root.occurrenceId,
                        terminalOccurrenceId: terminalOccurrenceId,
                        terminalOutcome: outcome.toDto(),
                        terminalAtEpochMillis: completedAt,
                        terminalRecordAck: chain.terminalRecordAck,
                        attempts: chain.attempts.sorted { $0.sequence < $1.sequence }.map { $0.toRecoveryAttemptDto() }
                    )
                }
            callback(IosAlarmNativeCleanupCandidatesResult(
                events: events,
                chains: chains,
                code: .success,
                message: nil
            ))
        } catch {
            callback(IosAlarmNativeCleanupCandidatesResult(
                events: [],
                chains: [],
                code: .sdkError,
                message: "Native cleanup candidates could not be read."
            ))
        }
    }

    func commitEventCleanup(
        eventId: String,
        fingerprint: String,
        callback: @escaping (IosAlarmNativeCleanupCommitResult) -> Void
    ) {
        do {
            let result = try withInboxFileLock { () -> IosAlarmNativeCleanupCommitResult in
                let state = try readStateUnlocked()
                guard let event = state.events.first(where: { $0.eventId == eventId }),
                      try eventFingerprint(event) == fingerprint,
                      event.consumedAtEpochMillis != nil,
                      Date().timeIntervalSince1970 * 1_000 >= Double(event.occurredAtEpochMillis + Self.occurrenceCoalescingWindowMillis),
                      !event.requiresRecordProjection || event.recordProjectionAckAtEpochMillis != nil else {
                    return IosAlarmNativeCleanupCommitResult(
                        committed: false,
                        stale: true,
                        code: .success,
                        message: nil
                    )
                }
                let remaining = state.events.filter { $0.eventId != eventId }
                try writeStateUnlocked(AlarmMissionEventInboxState(
                    schemaVersion: state.schemaVersion,
                    events: remaining,
                    dedupRecords: state.dedupRecords
                ))
                return IosAlarmNativeCleanupCommitResult(
                    committed: true,
                    stale: false,
                    code: .success,
                    message: nil
                )
            }
            callback(result)
        } catch {
            callback(IosAlarmNativeCleanupCommitResult(
                committed: false,
                stale: false,
                code: .sdkError,
                message: "Native cleanup could not be committed."
            ))
        }
    }

    func compactTerminalChain(
        chainId: String,
        fingerprint: String,
        callback: @escaping (IosAlarmNativeCleanupCommitResult) -> Void
    ) {
        do {
            // Lock order is Inbox -> journal. No journal operation acquires the Inbox lock.
            // Keeping the Inbox lock through compaction prevents a new event from racing the tombstone.
            let result = try withInboxFileLock { () -> IosAlarmNativeCleanupCommitResult in
                let state = try readStateUnlocked()
                let journalState = try AlarmRecoveryDriver.journal.recoveryState()
                var protectedOccurrenceIds = Set<String>()
                if let chain = journalState.chains[chainId] {
                    protectedOccurrenceIds.formUnion(chain.attempts.map(\.occurrenceId))
                    protectedOccurrenceIds.formUnion(chain.attempts.map(\.nextOccurrenceToken))
                    if let terminalOccurrenceId = chain.terminalOccurrenceId {
                        protectedOccurrenceIds.insert(terminalOccurrenceId)
                    }
                }
                if let tombstone = journalState.terminalTombstones[chainId] {
                    protectedOccurrenceIds.formUnion(tombstone.knownTokens)
                    protectedOccurrenceIds.insert(tombstone.terminalOccurrenceId)
                }
                guard !state.events.contains(where: {
                    $0.chainId == chainId || protectedOccurrenceIds.contains($0.occurrenceId)
                }) else {
                    return IosAlarmNativeCleanupCommitResult(
                        committed: false,
                        stale: true,
                        code: .success,
                        message: nil
                    )
                }
                _ = try AlarmRecoveryDriver.journal.compactTerminalChain(
                    chainId: chainId,
                    expectedFingerprint: fingerprint
                )
                return IosAlarmNativeCleanupCommitResult(
                    committed: true,
                    stale: false,
                    code: .success,
                    message: nil
                )
            }
            callback(result)
        } catch AlarmRecoveryJournalError.staleCleanupCandidate {
            callback(IosAlarmNativeCleanupCommitResult(
                committed: false,
                stale: true,
                code: .success,
                message: nil
            ))
        } catch {
            callback(IosAlarmNativeCleanupCommitResult(
                committed: false,
                stale: false,
                code: .sdkError,
                message: "Native cleanup could not be committed."
            ))
        }
    }

    func markConsumed(
        eventId: String,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        let consumed = markConsumed(eventId: eventId)
        callback(
            IosAlarmOperationResult(
                code: consumed ? .success : .notFound,
                message: nil
            )
        )
    }

    func ackRecordEventProjection(
        eventId: String,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        let acked = (try? mutateEvents { events, _ in
            guard let index = events.firstIndex(where: { $0.eventId == eventId }) else { return false }
            if events[index].recordProjectionAckAtEpochMillis == nil {
                events[index].recordProjectionAckAtEpochMillis = Int64(Date().timeIntervalSince1970 * 1_000)
            }
            return true
        }) ?? false
        callback(IosAlarmOperationResult(code: acked ? .success : .notFound, message: nil))
    }

    func upsertSourceSnapshot(
        snapshot: IosAlarmRecoverySourceSnapshotDto,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        completeOperation(callback) {
            let native = snapshot.toRecoverySnapshot()
            _ = try AlarmRecoveryDriver.journal.upsertSourceSnapshot(native)
            let key = AlarmRecoverySourceKey(sourceAlarmId: native.sourceAlarmId, scheduleVersion: native.scheduleVersion)
            if try AlarmRecoveryDriver.journal.recoveryState().sources[key] == nil {
                try AlarmRecoveryDriver.journal.setSourceEligibility(sourceAlarmId: native.sourceAlarmId,
                    scheduleVersion: native.scheduleVersion, eligibility: .enabled)
            }
        }
    }

    func setSourceEligibility(
        alarmId: String,
        scheduleVersion: Int64,
        eligibility: IosAlarmRecoveryEligibility,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        completeOperation(callback) {
            try AlarmRecoveryDriver.journal.setSourceEligibility(
                sourceAlarmId: alarmId,
                scheduleVersion: scheduleVersion,
                eligibility: eligibility.toRecoveryEligibility()
            )
        }
    }

    func recoveryState(callback: @escaping (IosAlarmRecoveryAttemptsResult) -> Void) {
        do {
            try importLegacyProjection()
            callback(
                IosAlarmRecoveryAttemptsResult(
                    attempts: try recoveryAttemptDtos(),
                    terminals: try recoveryTerminalDtos(),
                    code: .success,
                    message: nil
                )
            )
        } catch {
            callback(
                IosAlarmRecoveryAttemptsResult(
                    attempts: [],
                    terminals: [],
                    code: .sdkError,
                    message: error.localizedDescription
                )
            )
        }
    }

    func recoverReservations(callback: @escaping (IosAlarmRecoveryAttemptsResult) -> Void) {
        Task {
            do {
                try await AlarmRecoveryDriver.recover()
                let attempts = try recoveryAttemptDtos()
                let terminals = try recoveryTerminalDtos()
                completeOnMain {
                    callback(
                        IosAlarmRecoveryAttemptsResult(
                            attempts: attempts,
                            terminals: terminals,
                            code: .success,
                            message: nil
                        )
                    )
                }
            } catch {
                completeOnMain {
                    callback(
                        IosAlarmRecoveryAttemptsResult(
                            attempts: [],
                            terminals: [],
                            code: .sdkError,
                            message: error.localizedDescription
                        )
                    )
                }
            }
        }
    }

    func ackMissionProjection(
        occurrenceId: String,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        completeOperation(callback) {
            try AlarmRecoveryDriver.journal.ackMissionProjection(occurrenceId: occurrenceId)
        }
    }

    func ackRecordProjection(
        occurrenceId: String,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        completeOperation(callback) {
            try AlarmRecoveryDriver.journal.ackRecordProjection(occurrenceId: occurrenceId)
        }
    }

    func commitTerminal(
        chainId: String,
        outcome: IosAlarmRecoveryAttemptOutcome,
        completedAtEpochMillis: Int64,
        occurrenceId: String?,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        completeOperation(callback) {
            try AlarmRecoveryDriver.journal.markTerminal(
                chainId: chainId,
                outcome: outcome.toRecoveryAttemptOutcome(),
                occurrenceId: occurrenceId,
                now: Date(timeIntervalSince1970: Double(completedAtEpochMillis) / 1_000)
            )
        }
    }

    func ackTerminalRecord(
        chainId: String,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        completeOperation(callback) {
            try AlarmRecoveryDriver.journal.ackTerminalRecord(chainId: chainId)
        }
    }

    func protectedReservationUuids(callback: @escaping (IosAlarmProtectedReservationsResult) -> Void) {
        do {
            var protected = try AlarmRecoveryDriver.journal.protectedReservationUUIDs()
            if let legacy = UserDefaults.standard.stringArray(forKey: "ios.activeMission.deadlineAlarm.v1"),
               let id = legacy.first, UUID(uuidString: id) != nil {
                protected.insert(id)
            }
            callback(
                IosAlarmProtectedReservationsResult(
                    reservationUuids: protected,
                    code: .success,
                    message: nil
                )
            )
        } catch {
            callback(
                IosAlarmProtectedReservationsResult(
                    reservationUuids: [],
                    code: .sdkError,
                    message: error.localizedDescription
                )
            )
        }
    }

    func notifyRecoveryChanged() {
        notifyEventRecorded()
    }

    @discardableResult
    func recordOpenStopThroughRecoveryIfAvailable(
        alarmId: String,
        occurrenceId: String?,
        retryAttempt: Int = 0,
        scheduleVersion: Int64,
        systemAlarmId: String,
        now: Date = Date()
    ) async throws -> Bool {
        let disposition = try await AlarmRecoveryDriver.stop(
            alarmId: alarmId,
            occurrenceToken: occurrenceId,
            retryAttempt: retryAttempt,
            scheduleVersion: scheduleVersion,
            systemAlarmId: systemAlarmId,
            now: now
        )
        switch disposition {
        case .handled, .preserved:
            notifyRecoveryChanged()
            return true
        case .fallback:
            return false
        }
    }

    private func pendingEvents() throws -> [AlarmMissionEvent] {
        try withInboxFileLock { try readStateUnlocked().events.filter { !$0.isConsumed } }
    }

    @discardableResult
    private func markConsumed(eventId: String, now: Date = Date()) -> Bool {
        let consumedAtEpochMillis = Int64(now.timeIntervalSince1970 * 1_000)
        var consumedJournalProjection = false
        if (try? AlarmRecoveryDriver.journal.recoveryState()
            .chains.values
            .flatMap(\.attempts)
            .contains(where: { $0.occurrenceId == eventId })) == true {
            do {
                try AlarmRecoveryDriver.journal.ackMissionProjection(occurrenceId: eventId)
                consumedJournalProjection = true
            } catch {
                return false
            }
        }
        let consumedLegacyProjection = (try? withInboxFileLock {
            do {
                var state = try readStateUnlocked()
                var events = state.events
                guard let index = events.firstIndex(where: { $0.eventId == eventId }) else {
                    return false
                }
                guard events[index].consumedAtEpochMillis == nil else {
                    return true
                }
                events[index].consumedAtEpochMillis = consumedAtEpochMillis
                state.events = events
                try writeStateUnlocked(state)
                return true
            } catch {
                return false
            }
        }) ?? false
        return consumedJournalProjection || consumedLegacyProjection
    }

    private func mutateEvents<Result>(
        _ mutation: (inout [AlarmMissionEvent], [AlarmMissionEventDedupRecord]) throws -> Result
    ) throws -> Result {
        try withInboxFileLock {
            var state = try readStateUnlocked()
            var events = state.events
            let result = try mutation(&events, state.dedupRecords)
            state.events = events
            state.dedupRecords = mergedDedupRecords(state.dedupRecords, events: events)
            try writeStateUnlocked(state)
            return result
        }
    }

    private static let occurrenceCoalescingWindowMillis: Int64 = 10_000

    private func pendingEventDtos() throws -> [IosAlarmMissionEventDto] {
        try importLegacyProjection()
        let journalIds = Set(try AlarmRecoveryDriver.journal.attemptsForRecovery().map(\.occurrenceId))
        return try pendingEvents()
            .filter { !journalIds.contains($0.occurrenceId) }
            .filter { try isRoutableToKotlin($0) }
            .map { $0.toDto() }
    }

    private func recordProjectionEventDtos() throws -> [IosAlarmMissionEventDto] {
        try importLegacyProjection()
        let journalIds = Set(try AlarmRecoveryDriver.journal.attemptsForRecovery().map(\.occurrenceId))
        return try withInboxFileLock { try readStateUnlocked().events }
            .filter {
                ($0.action == .stop ||
                    $0.ringingObservedAtEpochMillis != nil ||
                    $0.ringingStoppedAtEpochMillis != nil) &&
                    $0.recordProjectionAckAtEpochMillis == nil &&
                    !journalIds.contains($0.occurrenceId)
            }
            .filter { try isRoutableToKotlin($0) }
            .sorted { $0.occurredAtEpochMillis < $1.occurredAtEpochMillis }
            .map { $0.toDto() }
    }

    private func isRoutableToKotlin(_ event: AlarmMissionEvent) throws -> Bool {
        let sourceKey = AlarmRecoverySourceKey(
            sourceAlarmId: event.alarmId,
            scheduleVersion: event.scheduleVersion ?? 1
        )
        let stopAt = Date(timeIntervalSince1970: Double(
            event.ringingStoppedAtEpochMillis ?? event.occurredAtEpochMillis
        ) / 1_000)
        switch try AlarmRecoveryDriver.journal.routeStop(
            occurrenceToken: event.occurrenceId,
            retryAttempt: event.retryAttempt ?? 0,
            sourceKey: sourceKey,
            stopAt: stopAt
        ) {
        case .unknownRetry, .compactedTerminal:
            return false
        case let .liveAttempt(attempt):
            // A known next token belongs to native recovery. If its legacy
            // import was rejected, do not let Kotlin reinterpret it as a root.
            return event.occurrenceId != attempt.nextOccurrenceToken
        case .newRoot:
            return true
        }
    }

    private func recoveryEventDtos(includeAcked: Bool) throws -> [IosAlarmMissionEventDto] {
        try AlarmRecoveryDriver.journal.attemptsForRecovery()
            .filter { includeAcked || !$0.missionProjectionAck }
            .map { $0.toMissionEventDto() }
    }

    private func importLegacyProjection() throws {
        let defaults = UserDefaults.standard
        let active = defaults.stringArray(forKey: "ios.activeMission.record.v2")
        let deadline = defaults.stringArray(forKey: "ios.activeMission.deadlineAlarm.v1")
        let legacyEvents = try withInboxFileLock { try readStateUnlocked().events }
        if let active, active.count >= 12,
           let limit = Int64(active[3]), let expires = Int64(active[4]),
           let stopped = Int64(active[5]), let lat = Double(active[7]), let lon = Double(active[8]) {
            let oldEvent = legacyEvents.first { $0.occurrenceId == active[1] }
            let owner: AlarmRecoveryOwner = oldEvent?.ownerCaptured == true
                ? oldEvent?.ownerAccountId.map(AlarmRecoveryOwner.capturedAccount) ?? .capturedGuest : .uncaptured
            let snapshot = AlarmRecoverySourceSnapshot(
                sourceAlarmId: active[0], scheduleVersion: active.count > 13 ? Int64(active[13]) ?? 1 : 1,
                destination: AlarmRecoveryDestinationSnapshot(name: active[2], latitude: lat, longitude: lon),
                limitSeconds: limit * 60, scheduledAtEpochMillis: nil,
                originalTime: active[6], arrivalRadiusMeters: Double(active[9]), repeatRule: nil,
                title: active[2], soundName: nil, soundURI: active[11] == "true" ? active[10] : nil, owner: owner
            )
            _ = try AlarmRecoveryDriver.journal.upsertSourceSnapshot(snapshot)
            let matching = deadline.flatMap { $0.count >= 4 && $0[1] == active[1] ? $0 : nil }
            do {
                _ = try AlarmRecoveryDriver.journal.importLegacy(
                    occurrenceId: active[1], snapshot: snapshot,
                    stopAt: Date(timeIntervalSince1970: Double(stopped) / 1_000),
                    reservationUUID: matching?[0], nextOccurrenceToken: matching?[2],
                    expiresAtEpochMillis: expires, sequence: active.count > 12 ? Int(active[12]) ?? 0 : 0
                )
            } catch AlarmRecoveryJournalError.terminalChain {
                // A permanent tombstone means this legacy projection is stale.
            }
        }
        // Only pending confirmed stops with an immutable source snapshot can be imported.
        // Other legacy events remain in their existing inbox for the old coordinator.
        for event in legacyEvents where !event.isConsumed && event.ringingStoppedAtEpochMillis != nil {
            let sourceKey = AlarmRecoverySourceKey(
                sourceAlarmId: event.alarmId,
                scheduleVersion: event.scheduleVersion ?? 1
            )
            let stopAt = Date(timeIntervalSince1970: Double(event.ringingStoppedAtEpochMillis!) / 1_000)
            switch try AlarmRecoveryDriver.journal.routeStop(
                occurrenceToken: event.occurrenceId,
                retryAttempt: event.retryAttempt ?? 0,
                sourceKey: sourceKey,
                stopAt: stopAt
            ) {
            case .unknownRetry, .compactedTerminal:
                continue
            case let .liveAttempt(parent):
                guard event.occurrenceId == parent.nextOccurrenceToken else { continue }
                let retryAttempt = event.retryAttempt ?? 0
                do {
                    _ = try AlarmRecoveryDriver.journal.acceptStop(
                        snapshot: parent.snapshot,
                        occurrenceToken: event.occurrenceId,
                        retryAttempt: retryAttempt == 0 ? parent.sequence + 1 : retryAttempt,
                        parentOccurrenceId: parent.occurrenceId,
                        stopAt: stopAt
                    )
                } catch AlarmRecoveryJournalError.terminalChain,
                        AlarmRecoveryJournalError.missingParentAttempt,
                        AlarmRecoveryJournalError.retryTokenMismatch {
                    // Keep ambiguous legacy retry data out of the Kotlin root path.
                }
                continue
            case .newRoot:
                break
            }
            guard event.retryAttempt ?? 0 == 0,
                  var snapshot = try AlarmRecoveryDriver.journal.sourceSnapshot(
                    sourceAlarmId: event.alarmId, scheduleVersion: event.scheduleVersion ?? 1
                  ), try AlarmRecoveryDriver.journal.recoveryState().sources[
                    AlarmRecoverySourceKey(sourceAlarmId: event.alarmId, scheduleVersion: event.scheduleVersion ?? 1)
                  ] == .enabled else { continue }
            snapshot = AlarmRecoverySourceSnapshot(
                sourceAlarmId: snapshot.sourceAlarmId, scheduleVersion: snapshot.scheduleVersion,
                destination: snapshot.destination, limitSeconds: snapshot.limitSeconds,
                scheduledAtEpochMillis: snapshot.scheduledAtEpochMillis, originalTime: snapshot.originalTime,
                arrivalRadiusMeters: snapshot.arrivalRadiusMeters, repeatRule: snapshot.repeatRule,
                title: snapshot.title, soundName: snapshot.soundName, soundURI: snapshot.soundURI,
                owner: event.ownerCaptured == true
                    ? event.ownerAccountId.map(AlarmRecoveryOwner.capturedAccount) ?? .capturedGuest : .uncaptured
            )
            do {
                _ = try AlarmRecoveryDriver.journal.importLegacy(
                    occurrenceId: event.occurrenceId, snapshot: snapshot,
                    stopAt: stopAt,
                    reservationUUID: nil
                )
            } catch AlarmRecoveryJournalError.terminalChain {
                // Keep the legacy record intact without recreating a compacted chain.
            }
        }
        if let terminal = defaults.stringArray(forKey: "ios.activeMission.pendingTerminal.v1"),
           terminal.count >= 3, let at = Int64(terminal[2]) {
            let chainId = terminal.count > 4 ? terminal[4] : terminal[0]
            let state = try AlarmRecoveryDriver.journal.recoveryState()
            if state.terminalTombstones[chainId] == nil {
                try AlarmRecoveryDriver.journal.markTerminal(
                    chainId: chainId,
                    outcome: terminal[1] == "SUCCESS" ? .terminalSucceeded : .terminalForcedStop,
                    occurrenceId: terminal[0],
                    now: Date(timeIntervalSince1970: Double(at) / 1_000)
                )
            }
        }
    }

    private func recoveryTerminalDtos() throws -> [IosAlarmRecoveryTerminalDto] {
        try AlarmRecoveryDriver.journal.recoveryState().chains.values.compactMap { chain in
            guard let outcome = chain.terminalOutcome, let at = chain.terminalAtEpochMillis,
                  let latest = chain.attempts.max(by: { $0.sequence < $1.sequence }) else { return nil }
            return IosAlarmRecoveryTerminalDto(
                chainId: chain.chainId, occurrenceId: chain.terminalOccurrenceId ?? latest.occurrenceId,
                outcome: outcome.toDto(), completedAtEpochMillis: at,
                terminalRecordAck: chain.terminalRecordAck
            )
        }
    }

    private func recoveryAttemptDtos() throws -> [IosAlarmRecoveryAttemptDto] {
        try AlarmRecoveryDriver.journal.attemptsForRecovery()
            .map { $0.toRecoveryAttemptDto() }
    }

    private func completeOperation(
        _ callback: @escaping (IosAlarmOperationResult) -> Void,
        _ operation: () throws -> Void
    ) {
        do {
            try operation()
            callback(IosAlarmOperationResult(code: .success, message: nil))
        } catch {
            callback(
                IosAlarmOperationResult(
                    code: .sdkError,
                    message: error.localizedDescription
                )
            )
        }
    }

    private func notifyEventRecorded() {
        let currentListener = lock.withLock { listener }
        DispatchQueue.main.async {
            currentListener?.onEventRecorded()
        }
    }

    private func readStateUnlocked() throws -> AlarmMissionEventInboxState {
        let url = try inboxURL()
        guard fileManager.fileExists(atPath: url.path) else {
            return .empty
        }
        let data = try Data(contentsOf: url)
        guard !data.isEmpty else {
            return .empty
        }
        do {
            let state = try decoder.decode(AlarmMissionEventInboxState.self, from: data)
            guard state.schemaVersion == AlarmMissionEventInboxState.currentSchemaVersion else {
                throw AlarmMissionEventInboxError.unsupportedSchemaVersion
            }
            return state
        } catch let error as AlarmMissionEventInboxError {
            throw error
        } catch {
            let legacyEvents = try decoder.decode([AlarmMissionEvent].self, from: data)
            return AlarmMissionEventInboxState(
                schemaVersion: AlarmMissionEventInboxState.currentSchemaVersion,
                events: legacyEvents,
                dedupRecords: mergedDedupRecords([], events: legacyEvents)
            )
        }
    }

    private func writeStateUnlocked(_ state: AlarmMissionEventInboxState) throws {
        guard state.schemaVersion == AlarmMissionEventInboxState.currentSchemaVersion else {
            throw AlarmMissionEventInboxError.unsupportedSchemaVersion
        }
        let url = try inboxURL()
        let directory = url.deletingLastPathComponent()
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        try applyProtectedFileAttributes(to: directory)
        let tempURL = directory.appendingPathComponent(".\(url.lastPathComponent).\(UUID().uuidString).tmp")
        do {
            try writeDurably(encoder.encode(state), to: tempURL)
            try applyProtectedFileAttributes(to: tempURL)
            if fileManager.fileExists(atPath: url.path) {
                _ = try fileManager.replaceItemAt(url, withItemAt: tempURL)
            } else {
                try fileManager.moveItem(at: tempURL, to: url)
            }
            try applyProtectedFileAttributes(to: url)
            try fsyncDirectory(directory)
        } catch {
            try? fileManager.removeItem(at: tempURL)
            throw error
        }
    }

    private func withInboxFileLock<T>(_ body: () throws -> T) throws -> T {
        try lock.withLock {
            let directory = try inboxURL().deletingLastPathComponent()
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
            try applyProtectedFileAttributes(to: directory)
            let lockURL = directory.appendingPathComponent("alarm-mission-events.lock")
            let fd = open(lockURL.path, O_CREAT | O_RDWR, S_IRUSR | S_IWUSR)
            guard fd != -1 else {
                throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
            }
            defer { close(fd) }
            try applyProtectedFileAttributes(to: lockURL)
            guard flock(fd, LOCK_EX) == 0 else {
                throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
            }
            defer { flock(fd, LOCK_UN) }
            return try body()
        }
    }

    private func mergedDedupRecords(
        _ existing: [AlarmMissionEventDedupRecord],
        events: [AlarmMissionEvent]
    ) -> [AlarmMissionEventDedupRecord] {
        var records = existing
        for event in events {
            let record = AlarmMissionEventDedupRecord(event: event)
            if let index = records.firstIndex(where: { $0.eventId == event.eventId }) {
                records[index] = record
            } else {
                records.append(record)
            }
        }
        return records
    }

    private func eventFingerprint(_ event: AlarmMissionEvent) throws -> String {
        let digest = SHA256.hash(data: try encoder.encode(event))
        return "sha256:" + digest.map { String(format: "%02x", $0) }.joined()
    }

    private func applyProtectedFileAttributes(to url: URL) throws {
        #if os(iOS)
        try fileManager.setAttributes(
            [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication],
            ofItemAtPath: url.path
        )
        #endif
    }

    private func writeDurably(_ data: Data, to url: URL) throws {
        let fd = open(url.path, O_CREAT | O_EXCL | O_WRONLY, S_IRUSR | S_IWUSR)
        guard fd != -1 else {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
        do {
            try data.withUnsafeBytes { rawBuffer in
                guard let baseAddress = rawBuffer.baseAddress else { return }
                var remaining = rawBuffer.count
                var pointer = baseAddress
                while remaining > 0 {
                    let written = Darwin.write(fd, pointer, remaining)
                    guard written > 0 else {
                        throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
                    }
                    remaining -= written
                    pointer = pointer.advanced(by: written)
                }
            }
            guard fsync(fd) == 0 else {
                throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
            }
        } catch {
            close(fd)
            try? fileManager.removeItem(at: url)
            throw error
        }
        close(fd)
    }

    private func fsyncDirectory(_ directory: URL) throws {
        let fd = open(directory.path, O_RDONLY)
        guard fd != -1 else {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
        defer { close(fd) }
        guard fsync(fd) == 0 else {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
    }

    private func inboxURL() throws -> URL {
        let baseURL = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return baseURL
            .appendingPathComponent("Ringout", isDirectory: true)
            .appendingPathComponent("alarm-mission-events.json", isDirectory: false)
    }
}

private extension AlarmMissionEvent {
    func toDto() -> IosAlarmMissionEventDto {
        IosAlarmMissionEventDto(
            eventId: eventId,
            alarmId: alarmId,
            occurrenceId: occurrenceId,
            action: action.iosAction,
            occurredAtEpochMillis: occurredAtEpochMillis,
            retryAttempt: Int32(retryAttempt ?? 0),
            ringingObservedAtEpochMillis: ringingObservedAtEpochMillis.map { KotlinLong(value: $0) },
            ringingStoppedAtEpochMillis: ringingStoppedAtEpochMillis.map { KotlinLong(value: $0) },
            scheduleVersion: scheduleVersion ?? 1,
            ownerAccountId: ownerAccountId,
            ownerCaptured: ownerCaptured ?? false,
            chainId: chainId
        )
    }
}

private extension AlarmRecoveryAttempt {
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

private extension AlarmRecoveryOwner {
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

private extension AlarmRecoverySourceSnapshot {
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

private extension AlarmRecoveryOwner {
    func toDto() -> IosAlarmRecoveryOwnerDto {
        IosAlarmRecoveryOwnerDto(kind: kind.toDto(), accountId: accountId)
    }
}

private extension AlarmRecoveryOwner.Kind {
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

private extension AlarmRecoveryReservationLedger {
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

private extension AlarmRecoveryReservationStatus {
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

private extension AlarmRecoveryAttemptOutcome {
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

private extension IosAlarmRecoverySourceSnapshotDto {
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

private extension IosAlarmRecoveryOwnerDto {
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

private extension IosAlarmRecoveryEligibility {
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

private extension IosAlarmRecoveryReservationStatus {
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

private extension IosAlarmRecoveryAttemptOutcome {
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

private func completeOnMain(_ completion: @escaping () -> Void) {
    DispatchQueue.main.async(execute: completion)
}

enum AlarmMissionEventInboxError: Error {
    case invalidAlarmId
    case unsupportedSchemaVersion
}

private extension NSLock {
    func withLock<T>(_ work: () throws -> T) rethrows -> T {
        lock()
        defer { unlock() }
        return try work()
    }
}
