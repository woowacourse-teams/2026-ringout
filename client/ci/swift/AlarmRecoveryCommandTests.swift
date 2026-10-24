import Foundation

// Compile with the real Foundation command and journal; only OS operations are fake.
@main
struct AlarmRecoveryCommandTests {
    static func main() async throws {
        try await `직접_종료는_예약을_한번_수행하고_최신_저널을_반환한다`()
        try await `재울림은_실제_UUID를_중지하고_부모_체인을_유지한다`()
        try await `중복_NOT_FOUND는_동일_실행만_채택한다`()
        try await `예약_실패는_FAILED를_저장하고_재예약하지_않는다`()
        try await `중지_실패와_미발견은_새_미션을_만들지_않는다`()
        try await `terminal_도중_늦은_예약_성공은_취소한다`()
        try await `legacy_UNKNOWN은_복구해도_재예약하지_않는다`()
        try await `토큰_없는_원본도_수락된_실행_ID를_반환한다`()
        try await `snapshot_미준비는_OS_중지보다_먼저_실패한다`()
        try await `OS_중지_후_저널_실패는_소리가_멈춘_실패로_구분한다`()
        try await `Intent와_직접_결과의_중복은_추가_예약을_만들지_않는다`()
        try await `compact된_실행은_중지와_미발견_모두_terminal_ID를_반환한다`()
        try await `OS_중지_대기중_compact되어도_최신_terminal_ID를_반환한다`()
        try await `수락뒤_중단된_PENDING은_직접_재시도에서_한번만_예약한다`()
        print("14 alarm recovery command tests passed")
    }

    final class OS {
        var stopped: [String] = []
        var scheduled: [AlarmRecoveryAttempt] = []
        var cancelled: [String] = []
        var stopCode: AlarmRecoveryOSStopCode = .success
        var scheduleFailure = false
        var onSchedule: (() throws -> Void)?
        var onStop: (() throws -> Void)?
        var directory: URL!
        func command(_ journal: AlarmRecoveryJournal) -> AlarmRecoveryCommand {
            AlarmRecoveryCommand(
                journal: journal,
                clock: { Date(timeIntervalSince1970: 1_000) },
                stopAlarm: { id in self.stopped.append(id); try self.onStop?(); return self.stopCode },
                schedule: { attempt in
                    self.scheduled.append(attempt)
                    try self.onSchedule?()
                    if self.scheduleFailure { throw NSError(domain: "fake-schedule", code: 42) }
                },
                contains: { id in self.scheduled.contains { $0.nextReservationUUID == id } && !self.cancelled.contains(id) },
                cancel: { self.cancelled.append($0) }
            )
        }
    }

    static func fixture(_ body: (AlarmRecoveryJournal, OS) async throws -> Void) async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let journal = AlarmRecoveryJournal(directoryURL: directory)
        let snapshot = AlarmRecoverySourceSnapshot(
            sourceAlarmId: "source", scheduleVersion: 1,
            destination: AlarmRecoveryDestinationSnapshot(name: "Home", latitude: 37, longitude: 127),
            limitSeconds: 600, scheduledAtEpochMillis: nil, originalTime: nil,
            arrivalRadiusMeters: nil, repeatRule: "WEEKLY:MO", title: nil,
            soundName: nil, soundURI: nil, owner: .capturedGuest
        )
        _ = try journal.upsertSourceSnapshot(snapshot)
        try journal.setSourceEligibility(sourceAlarmId: "source", scheduleVersion: 1, eligibility: .enabled)
        let os = OS()
        os.directory = directory
        try await body(journal, os)
    }

    static func dismiss(_ command: AlarmRecoveryCommand, systemId: String = "system", token: String = "observed", retry: Int = 0) async -> AlarmRecoveryDismissOutcome {
        await command.dismiss(alarmId: "source", occurrenceToken: token, retryAttempt: retry,
                              scheduleVersion: 1, systemAlarmId: systemId,
                              now: Date(timeIntervalSince1970: 1_000))
    }

    static func `직접_종료는_예약을_한번_수행하고_최신_저널을_반환한다`() async throws {
        try await fixture { journal, os in
            let result = await dismiss(os.command(journal))
            let attempt = result.state!.chains.values.first!.attempts.first!
            assert(result.disposition == .handled && result.osStopCode == .success)
            assert(os.stopped == ["system"] && os.scheduled.count == 1)
            assert(attempt.currentReservation.status == .confirmed)
            assert(attempt.nextReservationUUID == os.scheduled[0].nextReservationUUID)
            assert(attempt.expiresAtEpochMillis == 1_600_000)
            assert(attempt.missionProjectionAck == false && attempt.recordProjectionAck == false)
            let restored = AlarmRecoveryJournal(directoryURL: os.directory)
            let recovered = try restored.attemptsForRecovery().first!
            assert(recovered.nextReservationUUID == attempt.nextReservationUUID)
            try await os.command(restored).recover()
            assert(os.scheduled.count == 1)
        }
    }

    static func `재울림은_실제_UUID를_중지하고_부모_체인을_유지한다`() async throws {
        try await fixture { journal, os in
            let command = os.command(journal)
            let first = await dismiss(command).state!.chains.values.first!.attempts.first!
            let result = await dismiss(command, systemId: first.nextReservationUUID, token: first.nextOccurrenceToken, retry: 1)
            let attempts = result.state!.chains[first.chainId]!.attempts
            assert(attempts.count == 2 && os.scheduled.count == 2)
            assert(os.stopped.last == first.nextReservationUUID)
            assert(attempts[1].parentOccurrenceId == first.occurrenceId && attempts[1].sequence == 1)
            let duplicate = await dismiss(command, systemId: first.nextReservationUUID, token: first.nextOccurrenceToken, retry: 1)
            assert(duplicate.disposition == .handled && os.scheduled.count == 2)
        }
    }

    static func `중복_NOT_FOUND는_동일_실행만_채택한다`() async throws {
        try await fixture { journal, os in
            let command = os.command(journal)
            let first = await dismiss(command)
            os.stopCode = .notFound
            let duplicate = await dismiss(command)
            assert(duplicate.disposition == .handled && first.occurrenceId == duplicate.occurrenceId)
            assert(os.scheduled.count == 1)
            let unknown = await dismiss(command, token: "other")
            assert(unknown.disposition == .preserved && unknown.occurrenceId == nil)
            assert(os.scheduled.count == 1)
        }
    }

    static func `예약_실패는_FAILED를_저장하고_재예약하지_않는다`() async throws {
        try await fixture { journal, os in
            os.scheduleFailure = true
            let command = os.command(journal)
            let result = await dismiss(command)
            let attempt = result.state!.chains.values.first!.attempts.first!
            assert(attempt.currentReservation.status == .failed)
            assert(attempt.currentReservation.lastError == "fake-schedule:42")
            try await command.recover()
            assert(os.scheduled.count == 1)
        }
    }

    static func `중지_실패와_미발견은_새_미션을_만들지_않는다`() async throws {
        try await fixture { journal, os in
            os.stopCode = .failed
            let failed = await dismiss(os.command(journal))
            assert(failed.disposition == .failed && failed.state!.chains.isEmpty)
            os.stopCode = .notFound
            let absent = await dismiss(os.command(journal))
            assert(absent.disposition == .preserved && absent.state!.chains.isEmpty)
            assert(os.scheduled.isEmpty)
        }
    }

    static func `terminal_도중_늦은_예약_성공은_취소한다`() async throws {
        try await fixture { journal, os in
            os.onSchedule = {
                let attempt = try journal.attemptsForRecovery().first!
                try journal.markTerminal(chainId: attempt.chainId, outcome: .terminalForcedStop,
                                           occurrenceId: attempt.occurrenceId, now: Date(timeIntervalSince1970: 1_001))
            }
            let result = await dismiss(os.command(journal))
            let attempt = result.state!.chains.values.first!.attempts.first!
            assert(os.cancelled == [attempt.nextReservationUUID])
            assert(attempt.currentReservation.status == .cancellationConfirmed)
        }
    }

    static func `legacy_UNKNOWN은_복구해도_재예약하지_않는다`() async throws {
        try await fixture { journal, os in
            let snapshot = try journal.sourceSnapshot(sourceAlarmId: "source", scheduleVersion: 1)!
            let attempt = try journal.importLegacy(occurrenceId: "legacy", snapshot: snapshot,
                                                   stopAt: Date(timeIntervalSince1970: 1_000), reservationUUID: nil)
            assert(attempt.currentReservation.status == .unknown)
            os.stopCode = .notFound
            let replay = await dismiss(os.command(journal), token: "legacy")
            assert(replay.disposition == .handled && os.scheduled.isEmpty)
            try await os.command(journal).recover()
            assert(os.scheduled.isEmpty)
            let updated = try journal.attemptsForRecovery().first!
            assert(updated.nextReservationUUID == attempt.nextReservationUUID)
            assert(updated.expiresAtEpochMillis == attempt.expiresAtEpochMillis)
        }
    }
    static func `토큰_없는_원본도_수락된_실행_ID를_반환한다`() async throws {
        try await fixture { journal, os in
            let result = await os.command(journal).dismiss(alarmId: "source", occurrenceToken: nil,
                retryAttempt: 0, scheduleVersion: 1, systemAlarmId: "system",
                now: Date(timeIntervalSince1970: 1_000))
            let attempt = result.state!.chains.values.first!.attempts.first!
            assert(result.occurrenceId == attempt.occurrenceId && os.scheduled.count == 1)
            os.stopCode = .notFound
            let replay = await os.command(journal).dismiss(alarmId: "source", occurrenceToken: nil,
                retryAttempt: 0, scheduleVersion: 1, systemAlarmId: "system",
                now: Date(timeIntervalSince1970: 1_001))
            assert(replay.disposition == .handled && replay.occurrenceId == attempt.occurrenceId)
            let outsideWindow = await os.command(journal).dismiss(alarmId: "source", occurrenceToken: nil,
                retryAttempt: 0, scheduleVersion: 1, systemAlarmId: "system",
                now: Date(timeIntervalSince1970: 1_020))
            assert(outsideWindow.disposition == .preserved && outsideWindow.occurrenceId == nil)
            assert(os.scheduled.count == 1)
        }
    }

    static func `snapshot_미준비는_OS_중지보다_먼저_실패한다`() async throws {
        try await fixture { journal, os in
            let result = await os.command(journal).dismiss(alarmId: "missing", occurrenceToken: "unknown",
                retryAttempt: 0, scheduleVersion: 1, systemAlarmId: "system",
                now: Date(timeIntervalSince1970: 1_000))
            assert(result.disposition == .failed && result.osStopCode == .notAttempted)
            assert(os.stopped.isEmpty && os.scheduled.isEmpty)
        }
    }

    static func `OS_중지_후_저널_실패는_소리가_멈춘_실패로_구분한다`() async throws {
        try await fixture { journal, os in
            os.onStop = {
                try Data("corrupt".utf8).write(to: os.directory.appendingPathComponent("alarm-recovery-journal.json"))
            }
            let result = await dismiss(os.command(journal))
            assert(result.osStopCode == .success && result.disposition == .failed)
            assert(result.state == nil && result.message != nil && os.scheduled.isEmpty)
        }
    }

    static func `Intent와_직접_결과의_중복은_추가_예약을_만들지_않는다`() async throws {
        try await fixture { journal, os in
            let command = os.command(journal)
            _ = try await command.stop(alarmId: "source", occurrenceToken: "observed",
                scheduleVersion: 1, systemAlarmId: "system", now: Date(timeIntervalSince1970: 1_000))
            os.stopCode = .notFound
            let result = await dismiss(command)
            assert(result.disposition == .handled && result.occurrenceId != nil)
            assert(os.scheduled.count == 1)
        }
    }

    static func `compact된_실행은_중지와_미발견_모두_terminal_ID를_반환한다`() async throws {
        try await fixture { journal, os in
            let command = os.command(journal)
            let attempt = await dismiss(command).state!.chains.values.first!.attempts.first!
            try journal.markTerminal(chainId: attempt.chainId, outcome: .terminalSucceeded,
                                     occurrenceId: attempt.occurrenceId)
            try journal.ackRecordProjection(occurrenceId: attempt.occurrenceId)
            try journal.ackTerminalRecord(chainId: attempt.chainId)
            try journal.markCancellationConfirmed(reservationUUID: attempt.nextReservationUUID)
            let candidate = try journal.terminalCleanupSnapshots().first!
            _ = try journal.compactTerminalChain(chainId: attempt.chainId, expectedFingerprint: candidate.fingerprint)
            let stopped = await dismiss(command)
            assert(stopped.disposition == .handled && stopped.occurrenceId == attempt.occurrenceId)
            os.stopCode = .notFound
            let absent = await dismiss(command)
            assert(absent.disposition == .handled && absent.occurrenceId == attempt.occurrenceId)
            assert(os.scheduled.count == 1)
        }
    }

    static func `OS_중지_대기중_compact되어도_최신_terminal_ID를_반환한다`() async throws {
        for stopCode in [AlarmRecoveryOSStopCode.success, .notFound] {
            try await fixture { journal, os in
                let command = os.command(journal)
                let attempt = await dismiss(command).state!.chains.values.first!.attempts.first!
                os.stopCode = stopCode
                os.onStop = {
                    try journal.markTerminal(chainId: attempt.chainId, outcome: .terminalSucceeded,
                                             occurrenceId: attempt.occurrenceId)
                    try journal.ackRecordProjection(occurrenceId: attempt.occurrenceId)
                    try journal.ackTerminalRecord(chainId: attempt.chainId)
                    try journal.markCancellationConfirmed(reservationUUID: attempt.nextReservationUUID)
                    let candidate = try journal.terminalCleanupSnapshots().first!
                    _ = try journal.compactTerminalChain(chainId: attempt.chainId,
                                                        expectedFingerprint: candidate.fingerprint)
                }
                let result = await dismiss(command)
                assert(result.disposition == .handled && result.occurrenceId == attempt.occurrenceId)
                assert(result.state!.chains.isEmpty && result.state!.terminalTombstones.count == 1)
                assert(os.scheduled.count == 1)
            }
        }
    }

    static func `수락뒤_중단된_PENDING은_직접_재시도에서_한번만_예약한다`() async throws {
        for stopCode in [AlarmRecoveryOSStopCode.success, .notFound] {
            try await fixture { journal, os in
                let snapshot = try journal.sourceSnapshot(sourceAlarmId: "source", scheduleVersion: 1)!
                let committed = try journal.acceptStop(snapshot: snapshot, occurrenceToken: "observed",
                    stopAt: Date(timeIntervalSince1970: 1_000)).attempt
                assert(committed.currentReservation.status == .pending)
                os.stopCode = stopCode
                let command = os.command(journal)
                let result = await dismiss(command)
                let resumed = result.state!.chains[committed.chainId]!.attempts.first!
                assert(result.disposition == .handled && result.occurrenceId == committed.occurrenceId)
                assert(resumed.currentReservation.status == .confirmed)
                assert(resumed.nextReservationUUID == committed.nextReservationUUID)
                assert(resumed.expiresAtEpochMillis == committed.expiresAtEpochMillis)
                assert(os.scheduled.count == 1)
                _ = await dismiss(command)
                assert(os.scheduled.count == 1)
            }
        }
    }

}
