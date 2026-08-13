import Foundation
import Shared

struct AlarmMissionEvent: Codable, Equatable {
    let eventId: String
    let alarmId: String
    let occurrenceId: String
    let action: AlarmMissionEventAction
    let occurredAtEpochMillis: Int64
    let retryAttempt: Int?
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

final class RingoutAlarmMissionEventInbox: IosAlarmMissionEventInbox {
    static let shared = RingoutAlarmMissionEventInbox()

    private let fileManager: FileManager
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder
    private let lock = NSLock()

    private init(fileManager: FileManager = .default) {
        self.fileManager = fileManager
        encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        decoder = JSONDecoder()
    }

    @discardableResult
    func record(
        alarmId rawAlarmId: String,
        action: AlarmMissionEventAction,
        occurrenceId requestedOccurrenceId: String? = nil,
        retryAttempt: Int = 0,
        now: Date = Date()
    ) throws -> AlarmMissionEvent {
        guard let alarmUUID = UUID(uuidString: rawAlarmId) else {
            throw AlarmMissionEventInboxError.invalidAlarmId
        }

        let occurrenceId = requestedOccurrenceId ??
            "\(alarmUUID.uuidString):\(UUID().uuidString)"
        let event = AlarmMissionEvent(
            eventId: UUID().uuidString,
            alarmId: alarmUUID.uuidString,
            occurrenceId: occurrenceId,
            action: action,
            occurredAtEpochMillis: Int64(now.timeIntervalSince1970 * 1_000),
            retryAttempt: retryAttempt,
            consumedAtEpochMillis: nil
        )

        try mutateEvents { events in
            events.append(event)
        }
        return event
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

    private func mutateEvents(_ mutation: (inout [AlarmMissionEvent]) -> Void) throws {
        try lock.withLock {
            var events = try readEventsUnlocked()
            mutation(&events)
            try writeEventsUnlocked(events)
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
