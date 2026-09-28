import Foundation

struct AlarmRingingOccurrence: Codable, Equatable {
    let occurrenceId: String
    let observedAtEpochMillis: Int64
    var endedAtEpochMillis: Int64?
    var isAlerting: Bool
}

/// Shared by AlarmKit snapshots and stop intents, including after a process restart.
final class AlarmRingingOccurrenceStore {
    static let shared = AlarmRingingOccurrenceStore()
    private let directoryURL: URL?
    private let lock = NSLock()
    private let coalescingWindowMillis: Int64 = 10_000

    init(directoryURL: URL? = nil) {
        self.directoryURL = directoryURL
    }

    func observe(alertingIds: Set<String>, now: Date = Date()) throws -> [String: AlarmRingingOccurrence] {
        lock.lock()
        defer { lock.unlock() }
        let timestamp = Int64(now.timeIntervalSince1970 * 1_000)
        var sessions = try read()
        let previous = sessions
        for id in sessions.keys where !alertingIds.contains(id) {
            if sessions[id]?.isAlerting == true {
                sessions[id]?.isAlerting = false
                if sessions[id]?.endedAtEpochMillis == nil {
                    sessions[id]?.endedAtEpochMillis = timestamp
                }
            }
        }
        for id in alertingIds {
            if let current = sessions[id], current.isAlerting,
               current.endedAtEpochMillis.map({ timestamp <= $0 + coalescingWindowMillis }) ?? true {
                continue
            }
            sessions[id] = AlarmRingingOccurrence(
                occurrenceId: "\(id):\(UUID().uuidString)",
                observedAtEpochMillis: timestamp,
                endedAtEpochMillis: nil,
                isAlerting: true
            )
        }
        if sessions != previous { try write(sessions) }
        return sessions.filter { alertingIds.contains($0.key) }
    }

    func consume(
        systemAlarmId: String,
        requestedOccurrenceId: String?,
        now: Date = Date()
    ) throws -> AlarmRingingOccurrence? {
        lock.lock()
        defer { lock.unlock() }
        var sessions = try read()
        let key = sessions.first { requestedOccurrenceId != nil && $0.value.occurrenceId == requestedOccurrenceId }?.key
            ?? systemAlarmId
        guard var session = sessions[key] else { return nil }
        let timestamp = Int64(now.timeIntervalSince1970 * 1_000)
        guard timestamp >= session.observedAtEpochMillis,
              session.endedAtEpochMillis.map({ timestamp <= $0 + coalescingWindowMillis }) ?? true else {
            return nil
        }
        // Keep isAlerting until a non-alerting snapshot arrives: an in-flight stale
        // snapshot after a stop intent must not manufacture another occurrence.
        if session.endedAtEpochMillis == nil { session.endedAtEpochMillis = timestamp }
        sessions[key] = session
        try write(sessions)
        return session
    }

    private func fileURL() throws -> URL {
        let directory = try directoryURL ?? FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask,
            appropriateFor: nil, create: true
        ).appendingPathComponent("Ringout", isDirectory: true)
        return directory.appendingPathComponent("alarm-ringing-occurrences.json")
    }

    private func read() throws -> [String: AlarmRingingOccurrence] {
        let url = try fileURL()
        guard FileManager.default.fileExists(atPath: url.path) else { return [:] }
        return try JSONDecoder().decode([String: AlarmRingingOccurrence].self, from: Data(contentsOf: url))
    }

    private func write(_ sessions: [String: AlarmRingingOccurrence]) throws {
        let url = try fileURL()
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(sessions).write(to: url, options: [.atomic])
    }
}
