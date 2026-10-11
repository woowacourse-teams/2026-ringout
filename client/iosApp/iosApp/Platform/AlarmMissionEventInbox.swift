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
    var consumedAtEpochMillis: Int64?

    var isConsumed: Bool {
        consumedAtEpochMillis != nil
    }
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
        let event = try mutateEvents { events in
            let observation = try? AlarmRingingOccurrenceStore.shared.consume(
                systemAlarmId: systemAlarmId.flatMap { UUID(uuidString: $0)?.uuidString } ?? alarmId,
                requestedOccurrenceId: requestedOccurrenceId,
                now: now
            )
            let recentOccurrenceId = events.reversed().first { existing in
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
            // Preserve an explicitly captured guest, including across native/fallback duplicates.
            let owner: IosAlarmOccurrenceOwner
            if let previous {
                owner = IosAlarmOccurrenceOwner(accountId: previous.ownerAccountId, captured: previous.ownerCaptured ?? false)
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
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        do {
            try record(
                alarmId: alarmId,
                action: .open,
                occurrenceId: occurrenceId,
                retryAttempt: Int(retryAttempt),
                scheduleVersion: scheduleVersion,
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
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        Task {
            do {
                if try hasJournalRoute(
                    alarmId: alarmId,
                    occurrenceToken: occurrenceId,
                    scheduleVersion: scheduleVersion
                ) {
                    _ = try await AlarmRecoveryDriver.stop(
                        alarmId: alarmId,
                        occurrenceToken: occurrenceId,
                        scheduleVersion: scheduleVersion,
                        systemAlarmId: alarmId
                    )
                    completeOnMain {
                        self.notifyEventRecorded()
                        callback(IosAlarmOperationResult(code: .success, message: nil))
                    }
                    return
                }
                try record(
                    alarmId: alarmId,
                    action: .stop,
                    occurrenceId: occurrenceId,
                    retryAttempt: Int(retryAttempt),
                    scheduleVersion: scheduleVersion,
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
        scheduleVersion: Int64,
        systemAlarmId: String,
        now: Date = Date()
    ) async throws -> Bool {
        guard try hasJournalRoute(
            alarmId: alarmId,
            occurrenceToken: occurrenceId,
            scheduleVersion: scheduleVersion
        ) else {
            return false
        }
        _ = try await AlarmRecoveryDriver.stop(
            alarmId: alarmId,
            occurrenceToken: occurrenceId,
            scheduleVersion: scheduleVersion,
            systemAlarmId: systemAlarmId,
            now: now
        )
        notifyRecoveryChanged()
        return true
    }

    private func pendingEvents() throws -> [AlarmMissionEvent] {
        try lock.withLock {
            try readEventsUnlocked().filter { !$0.isConsumed }
        }
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
        let consumedLegacyProjection = lock.withLock {
            do {
                var events = try readEventsUnlocked()
                guard let index = events.firstIndex(where: { $0.eventId == eventId }) else {
                    return false
                }
                guard events[index].consumedAtEpochMillis == nil else {
                    return true
                }
                events[index].consumedAtEpochMillis = consumedAtEpochMillis
                try writeEventsUnlocked(events)
                return true
            } catch {
                return false
            }
        }
        return consumedJournalProjection || consumedLegacyProjection
    }

    private func mutateEvents<Result>(
        _ mutation: (inout [AlarmMissionEvent]) throws -> Result
    ) throws -> Result {
        try lock.withLock {
            var events = try readEventsUnlocked()
            let result = try mutation(&events)
            try writeEventsUnlocked(events)
            return result
        }
    }

    private static let occurrenceCoalescingWindowMillis: Int64 = 10_000

    private func pendingEventDtos() throws -> [IosAlarmMissionEventDto] {
        let journalIds = Set(try AlarmRecoveryDriver.journal.attemptsForRecovery().map(\.occurrenceId))
        return try pendingEvents().filter { !journalIds.contains($0.occurrenceId) }.map { $0.toDto() }
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
        let legacyEvents = try lock.withLock { try readEventsUnlocked() }
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
            _ = try AlarmRecoveryDriver.journal.importLegacy(
                occurrenceId: active[1], snapshot: snapshot,
                stopAt: Date(timeIntervalSince1970: Double(stopped) / 1_000),
                reservationUUID: matching?[0], nextOccurrenceToken: matching?[2],
                expiresAtEpochMillis: expires, sequence: active.count > 12 ? Int(active[12]) ?? 0 : 0
            )
        }
        // Only pending confirmed stops with an immutable source snapshot can be imported.
        // Other legacy events remain in their existing inbox for the old coordinator.
        for event in legacyEvents where !event.isConsumed && event.ringingStoppedAtEpochMillis != nil {
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
            _ = try AlarmRecoveryDriver.journal.importLegacy(
                occurrenceId: event.occurrenceId, snapshot: snapshot,
                stopAt: Date(timeIntervalSince1970: Double(event.ringingStoppedAtEpochMillis!) / 1_000),
                reservationUUID: nil
            )
        }
        if let terminal = defaults.stringArray(forKey: "ios.activeMission.pendingTerminal.v1"),
           terminal.count >= 3, let at = Int64(terminal[2]) {
            try AlarmRecoveryDriver.journal.markTerminal(
                chainId: terminal.count > 4 ? terminal[4] : terminal[0],
                outcome: terminal[1] == "SUCCESS" ? .terminalSucceeded : .terminalForcedStop,
                occurrenceId: terminal[0],
                now: Date(timeIntervalSince1970: Double(at) / 1_000)
            )
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

    private func hasJournalRoute(
        alarmId rawAlarmId: String,
        occurrenceToken: String?,
        scheduleVersion: Int64
    ) throws -> Bool {
        let alarmId = UUID(uuidString: rawAlarmId)?.uuidString ?? rawAlarmId
        let state = try AlarmRecoveryDriver.journal.recoveryState()
        if let occurrenceToken,
           state.chains.values.flatMap(\.attempts).contains(where: { $0.nextOccurrenceToken == occurrenceToken }) {
            return true
        }
        let key = AlarmRecoverySourceKey(sourceAlarmId: alarmId, scheduleVersion: scheduleVersion)
        return state.snapshots[key] != nil
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

    private func readEventsUnlocked() throws -> [AlarmMissionEvent] {
        let url = try inboxURL()
        guard fileManager.fileExists(atPath: url.path) else {
            return []
        }
        let data = try Data(contentsOf: url)
        guard !data.isEmpty else {
            return []
        }
        return try decoder.decode([AlarmMissionEvent].self, from: data)
    }

    private func writeEventsUnlocked(_ events: [AlarmMissionEvent]) throws {
        let url = try inboxURL()
        try fileManager.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        let data = try encoder.encode(events)
        try data.write(to: url, options: [.atomic])
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
            ownerCaptured: ownerCaptured ?? false
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
            ownerCaptured: snapshot.owner.dtoCaptured
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
            lateScheduleAfterTerminal: lateScheduleAfterTerminal
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
}

private extension NSLock {
    func withLock<T>(_ work: () throws -> T) rethrows -> T {
        lock()
        defer { unlock() }
        return try work()
    }
}
