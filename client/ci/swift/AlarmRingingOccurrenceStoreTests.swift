import Foundation

// Run with swiftc alongside iosApp/iosApp/Platform/AlarmRingingOccurrenceStore.swift.
@main
struct AlarmRingingOccurrenceStoreTests {
    static func main() throws {
        try `재시작해도_같은_울림의_ID와_최초_시각을_유지한다`()
        try `중지_이벤트와_지연된_스냅샷은_같은_실행을_사용한다`()
        try `다음_울림은_새_ID로_구분한다`()
        try `관찰하지_못한_울림은_시작_시각을_만들지_않는다`()
        try `재울림의_시스템_ID로_시작_시각을_찾는다`()
        print("5 ringing occurrence tests passed")
    }

    static func withStore(_ test: (URL, AlarmRingingOccurrenceStore) throws -> Void) throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        try test(directory, AlarmRingingOccurrenceStore(directoryURL: directory))
    }

    static func `재시작해도_같은_울림의_ID와_최초_시각을_유지한다`() throws {
        try withStore { directory, store in
            let first = try store.observe(alertingIds: ["alarm"], now: Date(timeIntervalSince1970: 1_000))["alarm"]!
            let restored = AlarmRingingOccurrenceStore(directoryURL: directory)
            let second = try restored.observe(alertingIds: ["alarm"], now: Date(timeIntervalSince1970: 1_200))["alarm"]!
            assert(first == second)
            assert(second.observedAtEpochMillis == 1_000_000)
        }
    }

    static func `중지_이벤트와_지연된_스냅샷은_같은_실행을_사용한다`() throws {
        try withStore { _, store in
            let first = try store.observe(alertingIds: ["alarm"], now: Date(timeIntervalSince1970: 1_000))["alarm"]!
            let stopped = try store.consume(systemAlarmId: "alarm", requestedOccurrenceId: nil, now: Date(timeIntervalSince1970: 1_001))!
            let stale = try store.observe(alertingIds: ["alarm"], now: Date(timeIntervalSince1970: 1_002))["alarm"]!
            _ = try store.observe(alertingIds: [], now: Date(timeIntervalSince1970: 1_003))
            let duplicate = try store.consume(systemAlarmId: "alarm", requestedOccurrenceId: first.occurrenceId, now: Date(timeIntervalSince1970: 1_004))!
            assert(first.occurrenceId == stopped.occurrenceId)
            assert(first.occurrenceId == stale.occurrenceId)
            assert(first.occurrenceId == duplicate.occurrenceId)
            assert(duplicate.endedAtEpochMillis == 1_001_000)
        }
    }

    static func `다음_울림은_새_ID로_구분한다`() throws {
        try withStore { _, store in
            let first = try store.observe(alertingIds: ["alarm"], now: Date(timeIntervalSince1970: 1_000))["alarm"]!
            _ = try store.observe(alertingIds: [], now: Date(timeIntervalSince1970: 1_010))
            let second = try store.observe(alertingIds: ["alarm"], now: Date(timeIntervalSince1970: 2_000))["alarm"]!
            assert(first.occurrenceId != second.occurrenceId)
            assert(second.observedAtEpochMillis == 2_000_000)
        }
    }

    static func `관찰하지_못한_울림은_시작_시각을_만들지_않는다`() throws {
        try withStore { _, store in
            let missing = try store.consume(systemAlarmId: "missing", requestedOccurrenceId: nil)
            assert(missing == nil)
            _ = try store.observe(alertingIds: ["alarm"], now: Date(timeIntervalSince1970: 1_000))
            _ = try store.observe(alertingIds: [], now: Date(timeIntervalSince1970: 1_001))
            let expired = try store.consume(systemAlarmId: "alarm", requestedOccurrenceId: nil, now: Date(timeIntervalSince1970: 2_000))
            assert(expired == nil)
        }
    }

    static func `재울림의_시스템_ID로_시작_시각을_찾는다`() throws {
        try withStore { _, store in
            _ = try store.observe(alertingIds: ["alarm", "retry-system"], now: Date(timeIntervalSince1970: 1_000))
            let stopped = try store.consume(systemAlarmId: "retry-system", requestedOccurrenceId: "mission:retry-1", now: Date(timeIntervalSince1970: 1_001))!
            assert(stopped.observedAtEpochMillis == 1_000_000)
            assert(stopped.occurrenceId.hasPrefix("retry-system:"))
        }
    }
}
