import Foundation
import Shared

struct AlarmMissionEvent: Codable, Equatable {
    let eventId: String
    let alarmId: String
    let occurrenceId: String
    let action: AlarmMissionEventAction
    let occurredAtEpochMillis: Int64
    let retryAttempt: Int?
    let source: AlarmMissionEventSource?
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
        source: AlarmMissionEventSource = .nativeIntent,
        now: Date = Date()
    ) throws -> AlarmMissionEvent {
        guard let alarmUUID = UUID(uuidString: rawAlarmId) else {
            throw AlarmMissionEventInboxError.invalidAlarmId
        }

        let alarmId = alarmUUID.uuidString
        let occurredAtEpochMillis = Int64(now.timeIntervalSince1970 * 1_000)
        let event = try mutateEvents { events in
            let recentOccurrenceId = events.reversed().first { existing in
                let existingRetryAttempt = existing.retryAttempt ?? 0
                let elapsedMillis = occurredAtEpochMillis - existing.occurredAtEpochMillis
                let isFallbackDeliveryPair =
                    (source == .runtimeFallback && existing.source == .nativeIntent) ||
                    (source == .nativeIntent && existing.source == .runtimeFallback) ||
                    (source == .runtimeFallback && existing.source == .runtimeFallback)
                return existing.alarmId == alarmId &&
                    existingRetryAttempt == retryAttempt &&
                    isFallbackDeliveryPair &&
                    elapsedMillis >= 0 &&
                    elapsedMillis <= Self.occurrenceCoalescingWindowMillis
            }?.occurrenceId
            let occurrenceId = requestedOccurrenceId ?? recentOccurrenceId ??
                "\(alarmId):\(UUID().uuidString)"
            let event = AlarmMissionEvent(
                eventId: UUID().uuidString,
                alarmId: alarmId,
                occurrenceId: occurrenceId,
                action: action,
                occurredAtEpochMillis: occurredAtEpochMillis,
                retryAttempt: retryAttempt,
                source: source,
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
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        do {
            try record(
                alarmId: alarmId,
                action: .open,
                occurrenceId: occurrenceId,
                retryAttempt: Int(retryAttempt),
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
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        do {
            try record(
                alarmId: alarmId,
                action: .stop,
                occurrenceId: occurrenceId,
                retryAttempt: Int(retryAttempt),
                source: .runtimeFallback
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

    func pendingEvents(callback: @escaping (IosAlarmMissionEventsResult) -> Void) {
        do {
            callback(
                IosAlarmMissionEventsResult(
                    events: try pendingEvents().map { event in
                        IosAlarmMissionEventDto(
                            eventId: event.eventId,
                            alarmId: event.alarmId,
                            occurrenceId: event.occurrenceId,
                            action: event.action.iosAction,
                            occurredAtEpochMillis: event.occurredAtEpochMillis,
                            retryAttempt: Int32(event.retryAttempt ?? 0)
                        )
                    },
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

    private func pendingEvents() throws -> [AlarmMissionEvent] {
        try lock.withLock {
            try readEventsUnlocked().filter { !$0.isConsumed }
        }
    }

    @discardableResult
    private func markConsumed(eventId: String, now: Date = Date()) -> Bool {
        let consumedAtEpochMillis = Int64(now.timeIntervalSince1970 * 1_000)
        return lock.withLock {
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
    }

    private func mutateEvents<Result>(
        _ mutation: (inout [AlarmMissionEvent]) -> Result
    ) throws -> Result {
        try lock.withLock {
            var events = try readEventsUnlocked()
            let result = mutation(&events)
            try writeEventsUnlocked(events)
            return result
        }
    }

    private static let occurrenceCoalescingWindowMillis: Int64 = 10_000

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
