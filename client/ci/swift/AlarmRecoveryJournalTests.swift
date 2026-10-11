import Foundation

// Run with swiftc alongside iosApp/iosApp/Platform/AlarmRecoveryJournal.swift.
@main
struct AlarmRecoveryJournalTests {
    static func main() throws {
        if CommandLine.arguments.count == 4, CommandLine.arguments[1] == "reader" {
            let journal = AlarmRecoveryJournal(directoryURL: URL(fileURLWithPath: CommandLine.arguments[2]))
            let reads = Int(CommandLine.arguments[3])!
            for _ in 0..<reads {
                _ = try journal.attemptsForRecovery()
            }
            return
        }
        if CommandLine.arguments.count == 5 {
            let journal = AlarmRecoveryJournal(directoryURL: URL(fileURLWithPath: CommandLine.arguments[2]))
            let snapshot = testSnapshot(sourceAlarmId: CommandLine.arguments[3], scheduleVersion: 1)
            try enable(snapshot, in: journal)
            _ = try journal.acceptStop(
                snapshot: snapshot,
                occurrenceToken: CommandLine.arguments[4],
                stopAt: Date(timeIntervalSince1970: 1_000)
            )
            return
        }

        try `루트_중지는_재시작해도_chainId와_occurrenceId를_유지한다`()
        try `명시적_토큰_중복은_예약_ID와_시간을_보존한다`()
        try `토큰_없는_루트는_십초_창에서만_휴리스틱으로_합쳐진다`()
        try `재울림은_부모_체인과_스냅샷을_유지하고_새_occurrence를_만든다`()
        try `삭제된_소스는_새_루트만_막고_기존_체인은_막지_않는다`()
        try `예약_claim은_재시작_후_시간만으로_빼앗기지_않는다`()
        try `terminal_이후_늦은_예약_성공은_취소필요로_남는다`()
        try `OS_조회_부재는_inflight를_재발행하지_않고_UNKNOWN으로_남긴다`()
        try `mission_ACK와_record_ACK는_분리되고_모든_시도는_복구에_남는다`()
        try `손상된_저널은_빈_상태로_덮어쓰지_않는다`()
        try `다른_프로세스의_동시_저장은_유실되지_않는다`()
        try `legacy_import는_멱등이며_기존_ID를_chain으로_사용한다`()
        try `source_snapshot은_버전별로_처음_값을_보존한다`()
        try `terminal_record_ACK는_mission_record_ACK와_분리된다`()
        try `terminal_취소확인_후_예약이_다시_보이면_취소의무가_되살아난다`()
        try `미정산_schedule_call은_취소확인으로_닫히지_않는다`()
        try `지난_due는_새_claim을_만들지_않고_expired로_남긴다`()
        try `재울림은_부모_스냅샷을_강제로_채택한다`()
        try `잘못된_재울림_토큰과_terminal_parent는_거절한다`()
        try `비활성화_뒤_지연된_중복_루트는_복원된다`()
        try `동시_reader_writer가_손상_없이_끝난다`()
        try `뒤늦은_재울림이_있어도_종료된_울림_ID를_보존한다`()
        try `관측된_원본_울림은_기존_실행_ID를_체인_ID로_채택한다`()
        try `미지원_schema는_변경하지_않고_실패한다`()
        try `앱_전환_관측은_마감과_예약을_바꾸지_않고_개수를_제한한다`()
        print("25 alarm recovery journal tests passed")
    }

    static func `뒤늦은_재울림이_있어도_종료된_울림_ID를_보존한다`() throws {
        try withJournal { directory, journal in
            try enable(testSnapshot(), in: journal)
            let root = try journal.acceptStop(snapshot: testSnapshot(), occurrenceToken: "root-stop",
                stopAt: Date(timeIntervalSince1970: 1_000)).attempt
            _ = try journal.acceptStop(snapshot: testSnapshot(), occurrenceToken: root.nextOccurrenceToken,
                parentOccurrenceId: root.occurrenceId, stopAt: Date(timeIntervalSince1970: 1_600))
            try journal.markTerminal(chainId: root.chainId, outcome: .terminalSucceeded,
                occurrenceId: root.occurrenceId)
            let restored = try AlarmRecoveryJournal(directoryURL: directory).recoveryState()
            assert(restored.chains[root.chainId]?.terminalOccurrenceId == root.occurrenceId)
            assert(restored.chains[root.chainId]?.attempts.allSatisfy { $0.currentReservation.cancellationRequired } == true)
        }
    }

    static func `관측된_원본_울림은_기존_실행_ID를_체인_ID로_채택한다`() throws {
        try withJournal { _, journal in
            try enable(testSnapshot(), in: journal)
            let stop = try journal.acceptStop(snapshot: testSnapshot(), occurrenceToken: "observed",
                rootOccurrenceId: "observed", stopAt: Date(timeIntervalSince1970: 1_000)).attempt
            assert(stop.chainId == "observed" && stop.occurrenceId == "observed")
            let duplicate = try journal.acceptStop(snapshot: testSnapshot(), occurrenceToken: "observed",
                stopAt: Date(timeIntervalSince1970: 9_000))
            assert(duplicate.duplicate && duplicate.attempt == stop)
        }
    }

    static func `미지원_schema는_변경하지_않고_실패한다`() throws {
        try withJournal { directory, journal in
            try enable(testSnapshot(), in: journal)
            let file = directory.appendingPathComponent("alarm-recovery-journal.json")
            var json = try JSONSerialization.jsonObject(with: Data(contentsOf: file)) as! [String: Any]
            json["schemaVersion"] = 99
            let data = try JSONSerialization.data(withJSONObject: json)
            try data.write(to: file)
            do {
                try journal.setSourceEligibility(sourceAlarmId: "alarm", scheduleVersion: 1, eligibility: .deleted)
                assertionFailure("Unsupported schema should fail closed")
            } catch AlarmRecoveryJournalError.unsupportedSchemaVersion { }
            let preserved = try Data(contentsOf: file)
            assert(preserved == data)
        }
    }

    static func `앱_전환_관측은_마감과_예약을_바꾸지_않고_개수를_제한한다`() throws {
        try withJournal { directory, journal in
            try enable(testSnapshot(), in: journal)
            let attempt = try journal.acceptStop(snapshot: testSnapshot(), occurrenceToken: nil).attempt
            for index in 0..<40 {
                try journal.recordPresentation(stage: "test-\(index)", mode: "background",
                    canContinueInForeground: true, protectedDataAvailable: false)
            }
            let restored = try AlarmRecoveryJournal(directoryURL: directory).recoveryState()
            assert(restored.chains[attempt.chainId]?.attempts.first == attempt)
            assert(restored.presentationObservations?.count == 32)
            assert(restored.presentationObservations?.first?.stage == "test-8")
        }
    }

    static func withJournal(
        ids: [String] = [],
        _ body: (URL, AlarmRecoveryJournal) throws -> Void
    ) throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        var remainingIds = ids
        let journal = AlarmRecoveryJournal(directoryURL: directory) {
            if remainingIds.isEmpty { return UUID().uuidString }
            return remainingIds.removeFirst()
        }
        try body(directory, journal)
    }

    static func testSnapshot(
        sourceAlarmId: String = "alarm",
        scheduleVersion: Int64 = 1,
        owner: AlarmRecoveryOwner = .uncaptured
    ) -> AlarmRecoverySourceSnapshot {
        AlarmRecoverySourceSnapshot(
            sourceAlarmId: sourceAlarmId,
            scheduleVersion: scheduleVersion,
            destination: AlarmRecoveryDestinationSnapshot(name: "Home", latitude: 37.0, longitude: 127.0),
            limitSeconds: 600,
            scheduledAtEpochMillis: 1_000_000,
            originalTime: "07:00",
            arrivalRadiusMeters: 50,
            repeatRule: "WEEKLY:MO",
            title: "Wake",
            soundName: "bell",
            soundURI: "ringout://sound/bell",
            owner: owner
        )
    }

    static func enable(_ snapshot: AlarmRecoverySourceSnapshot, in journal: AlarmRecoveryJournal) throws {
        _ = try journal.upsertSourceSnapshot(snapshot)
        try journal.setSourceEligibility(
            sourceAlarmId: snapshot.sourceAlarmId,
            scheduleVersion: snapshot.scheduleVersion,
            eligibility: .enabled
        )
    }

    static func `루트_중지는_재시작해도_chainId와_occurrenceId를_유지한다`() throws {
        try withJournal(ids: ["root", "reservation", "retry-token"]) { directory, journal in
            try enable(testSnapshot(), in: journal)
            let first = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "occurrence-token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            let restored = AlarmRecoveryJournal(directoryURL: directory)
            let duplicate = try restored.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "occurrence-token",
                stopAt: Date(timeIntervalSince1970: 9_000)
            )
            assert(duplicate.duplicate)
            assert(first.chainId == "root")
            assert(first.occurrenceId == first.chainId)
            assert(duplicate.attempt == first)
            assert(duplicate.attempt.stopAtEpochMillis == 1_000_000)
        }
    }

    static func `명시적_토큰_중복은_예약_ID와_시간을_보존한다`() throws {
        try withJournal(ids: ["root", "reservation", "retry-token"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let first = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "exact",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            let second = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "exact",
                stopAt: Date(timeIntervalSince1970: 1_500)
            ).attempt
            assert(second.nextReservationUUID == first.nextReservationUUID)
            assert(second.nextOccurrenceToken == first.nextOccurrenceToken)
            assert(second.expiresAtEpochMillis == 1_600_000)
        }
    }

    static func `토큰_없는_루트는_십초_창에서만_휴리스틱으로_합쳐진다`() throws {
        try withJournal(ids: ["root-a", "reservation-a", "retry-a", "root-b", "reservation-b", "retry-b"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let first = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: nil,
                stopAt: Date(timeIntervalSince1970: 1_000)
            )
            let sameWindow = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: nil,
                stopAt: Date(timeIntervalSince1970: 1_009)
            )
            let nextWindow = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: nil,
                stopAt: Date(timeIntervalSince1970: 1_011)
            )
            assert(sameWindow.duplicate)
            assert(sameWindow.attempt.occurrenceId == first.attempt.occurrenceId)
            assert(!nextWindow.duplicate)
            assert(nextWindow.attempt.occurrenceId != first.attempt.occurrenceId)
        }
    }

    static func `재울림은_부모_체인과_스냅샷을_유지하고_새_occurrence를_만든다`() throws {
        try withJournal(ids: ["root", "r1", "t1", "retry-occ", "r2", "t2"]) { _, journal in
            try enable(testSnapshot(sourceAlarmId: "root-source", scheduleVersion: 7), in: journal)
            let root = try journal.acceptStop(
                snapshot: testSnapshot(sourceAlarmId: "root-source", scheduleVersion: 7),
                occurrenceToken: "root-token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            let retry = try journal.acceptStop(
                snapshot: root.snapshot,
                occurrenceToken: root.nextOccurrenceToken,
                parentOccurrenceId: root.occurrenceId,
                stopAt: Date(timeIntervalSince1970: 1_060)
            ).attempt
            assert(retry.chainId == root.chainId)
            assert(retry.occurrenceId == root.nextOccurrenceToken)
            assert(retry.parentOccurrenceId == root.occurrenceId)
            assert(retry.sequence == 1)
            assert(retry.snapshot == root.snapshot)
        }
    }

    static func `삭제된_소스는_새_루트만_막고_기존_체인은_막지_않는다`() throws {
        try withJournal(ids: ["root", "r1", "t1", "retry", "r2", "t2"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let root = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "root",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            try journal.setSourceEligibility(sourceAlarmId: "alarm", scheduleVersion: 1, eligibility: .deleted)
            do {
                _ = try journal.acceptStop(
                    snapshot: testSnapshot(),
                    occurrenceToken: "new-root",
                    stopAt: Date(timeIntervalSince1970: 1_001)
                )
                fatalError("deleted source must reject new roots")
            } catch AlarmRecoveryJournalError.sourceNotEligible {}
            let retry = try journal.acceptStop(
                snapshot: root.snapshot,
                occurrenceToken: root.nextOccurrenceToken,
                parentOccurrenceId: root.occurrenceId,
                stopAt: Date(timeIntervalSince1970: 1_060)
            ).attempt
            assert(retry.chainId == root.chainId)
        }
    }

    static func `예약_claim은_재시작_후_시간만으로_빼앗기지_않는다`() throws {
        try withJournal(ids: ["root", "reservation", "retry-token"]) { directory, journal in
            try enable(testSnapshot(), in: journal)
            let attempt = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            let claim = try journal.claimNextReservation(
                occurrenceId: attempt.occurrenceId,
                operationId: "op-1",
                executionEpoch: "epoch-a",
                now: Date(timeIntervalSince1970: 1_001)
            )
            assert(claim != nil)
            let restored = AlarmRecoveryJournal(directoryURL: directory)
            let stolen = try restored.claimNextReservation(
                occurrenceId: attempt.occurrenceId,
                operationId: "op-2",
                executionEpoch: "epoch-b",
                now: Date(timeIntervalSince1970: 1_300)
            )
            assert(stolen == nil)
            let current = try restored.recoveryState().findAttemptForTest(attempt.occurrenceId)!
            assert(current.currentReservation.operationId == "op-1")
            assert(current.currentReservation.status == .inFlight)
        }
    }

    static func `terminal_이후_늦은_예약_성공은_취소필요로_남는다`() throws {
        try withJournal(ids: ["root", "reservation", "retry-token"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let attempt = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            let claim = try journal.claimNextReservation(
                occurrenceId: attempt.occurrenceId,
                operationId: "op",
                executionEpoch: "epoch",
                now: Date(timeIntervalSince1970: 1_001)
            )!
            try journal.markTerminal(
                chainId: attempt.chainId,
                outcome: .terminalSucceeded,
                now: Date(timeIntervalSince1970: 1_002)
            )
            try journal.completeReservation(
                claim: claim,
                status: .confirmed,
                now: Date(timeIntervalSince1970: 1_003)
            )
            let restored = try journal.recoveryState().findAttemptForTest(attempt.occurrenceId)!
            assert(restored.currentReservation.lateScheduleAfterTerminal)
            assert(restored.currentReservation.cancellationRequired)
            assert(restored.currentReservation.status == .cancellationRequired)
            let protectedIds = try journal.protectedReservationUUIDs()
            assert(protectedIds.contains("reservation"))
        }
    }

    static func `OS_조회_부재는_inflight를_재발행하지_않고_UNKNOWN으로_남긴다`() throws {
        try withJournal(ids: ["root", "reservation", "retry-token"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let attempt = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            _ = try journal.claimNextReservation(
                occurrenceId: attempt.occurrenceId,
                operationId: "op",
                executionEpoch: "epoch",
                now: Date(timeIntervalSince1970: 1_001)
            )
            try journal.recordReservationPresence(
                reservationUUID: "reservation",
                presence: .absent,
                now: Date(timeIntervalSince1970: 1_002)
            )
            let afterQuery = try journal.recoveryState().findAttemptForTest(attempt.occurrenceId)!
            assert(afterQuery.currentReservation.status == .unknown)
            let retryClaim = try journal.claimNextReservation(
                occurrenceId: attempt.occurrenceId,
                operationId: "op-2",
                executionEpoch: "epoch-2",
                now: Date(timeIntervalSince1970: 1_003)
            )
            assert(retryClaim == nil)
        }
    }

    static func `mission_ACK와_record_ACK는_분리되고_모든_시도는_복구에_남는다`() throws {
        try withJournal(ids: ["root", "r1", "t1", "retry", "r2", "t2"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let root = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "root",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            let retry = try journal.acceptStop(
                snapshot: root.snapshot,
                occurrenceToken: root.nextOccurrenceToken,
                parentOccurrenceId: root.occurrenceId,
                stopAt: Date(timeIntervalSince1970: 1_060)
            ).attempt
            try journal.ackMissionProjection(occurrenceId: retry.occurrenceId)
            try journal.ackRecordProjection(occurrenceId: root.occurrenceId)
            let attempts = try journal.attemptsForRecovery()
            assert(attempts.count == 2)
            assert(attempts.first { $0.occurrenceId == root.occurrenceId }!.recordProjectionAck)
            assert(!attempts.first { $0.occurrenceId == root.occurrenceId }!.missionProjectionAck)
            assert(attempts.first { $0.occurrenceId == retry.occurrenceId }!.missionProjectionAck)
            assert(!attempts.first { $0.occurrenceId == retry.occurrenceId }!.recordProjectionAck)
        }
    }

    static func `손상된_저널은_빈_상태로_덮어쓰지_않는다`() throws {
        try withJournal(ids: ["root", "reservation", "retry-token"]) { directory, journal in
            try enable(testSnapshot(), in: journal)
            _ = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            )
            let file = directory.appendingPathComponent("alarm-recovery-journal.json")
            let damaged = Data("not-json".utf8)
            try damaged.write(to: file)
            do {
                _ = try journal.acceptStop(
                    snapshot: testSnapshot(),
                    occurrenceToken: "next",
                    stopAt: Date(timeIntervalSince1970: 1_001)
                )
                fatalError("corrupt journal must fail closed")
            } catch is DecodingError {}
            let preserved = try Data(contentsOf: file)
            assert(preserved == damaged)
        }
    }

    static func `다른_프로세스의_동시_저장은_유실되지_않는다`() throws {
        try withJournal { directory, journal in
            let tokens = (0..<8).map { "token-\($0)" }
            let children = try tokens.map { token -> Process in
                let process = Process()
                process.executableURL = URL(fileURLWithPath: CommandLine.arguments[0])
                process.arguments = ["child", directory.path, "alarm", token]
                try process.run()
                return process
            }
            for child in children {
                child.waitUntilExit()
                assert(child.terminationStatus == 0)
            }
            let attempts = try journal.attemptsForRecovery()
            assert(attempts.count == 8)
            assert(Set(attempts.map(\.stopDedupKey)).count == 8)
        }
    }

    static func `legacy_import는_멱등이며_기존_ID를_chain으로_사용한다`() throws {
        try withJournal(ids: ["legacy-reservation", "legacy-next"]) { _, journal in
            let imported = try journal.importLegacy(
                occurrenceId: "legacy-occurrence",
                snapshot: testSnapshot(owner: .capturedAccount("account-1")),
                stopAt: Date(timeIntervalSince1970: 1_000),
                reservationUUID: "known-reservation"
            )
            let duplicate = try journal.importLegacy(
                occurrenceId: "legacy-occurrence",
                snapshot: testSnapshot(owner: .capturedAccount("account-2")),
                stopAt: Date(timeIntervalSince1970: 2_000),
                reservationUUID: "other"
            )
            assert(imported == duplicate)
            assert(imported.chainId == "legacy-occurrence")
            assert(imported.currentReservation.status == .confirmed)
            assert(imported.snapshot.owner.accountId == "account-1")
        }
    }

    static func `source_snapshot은_버전별로_처음_값을_보존한다`() throws {
        try withJournal { _, journal in
            let first = testSnapshot(sourceAlarmId: "alarm", scheduleVersion: 3, owner: .uncaptured)
            let second = testSnapshot(sourceAlarmId: "alarm", scheduleVersion: 3, owner: .capturedAccount("later"))
            let storedFirst = try journal.upsertSourceSnapshot(first)
            let storedSecond = try journal.upsertSourceSnapshot(second)
            let loaded = try journal.sourceSnapshot(sourceAlarmId: "alarm", scheduleVersion: 3)
            assert(storedFirst == first)
            assert(storedSecond == first)
            assert(loaded == first)
        }
    }

    static func `terminal_record_ACK는_mission_record_ACK와_분리된다`() throws {
        try withJournal(ids: ["root", "reservation", "retry-token"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let attempt = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            try journal.markTerminal(
                chainId: attempt.chainId,
                outcome: .terminalForcedStop,
                now: Date(timeIntervalSince1970: 1_001)
            )
            try journal.ackTerminalRecord(chainId: attempt.chainId)
            let chain = try journal.recoveryState().chains[attempt.chainId]!
            assert(chain.terminalRecordAck)
            assert(chain.attempts.first!.recordProjectionAck == false)
        }
    }

    static func `terminal_취소확인_후_예약이_다시_보이면_취소의무가_되살아난다`() throws {
        try withJournal(ids: ["root", "reservation", "retry-token"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let attempt = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            try journal.markTerminal(
                chainId: attempt.chainId,
                outcome: .terminalSucceeded,
                now: Date(timeIntervalSince1970: 1_001)
            )
            try journal.markCancellationConfirmed(
                reservationUUID: "reservation",
                now: Date(timeIntervalSince1970: 1_002)
            )
            try journal.recordReservationPresence(
                reservationUUID: "reservation",
                presence: .confirmed,
                now: Date(timeIntervalSince1970: 1_003)
            )
            let current = try journal.recoveryState().findAttemptForTest(attempt.occurrenceId)!
            assert(current.currentReservation.status == .cancellationRequired)
            assert(current.currentReservation.cancellationRequired)
            assert(current.currentReservation.cancellationConfirmedAtEpochMillis == nil)
        }
    }

    static func `미정산_schedule_call은_취소확인으로_닫히지_않는다`() throws {
        try withJournal(ids: ["root", "reservation", "retry-token"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let attempt = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            _ = try journal.claimNextReservation(
                occurrenceId: attempt.occurrenceId,
                operationId: "op",
                executionEpoch: "epoch",
                now: Date(timeIntervalSince1970: 1_001)
            )
            try journal.markTerminal(
                chainId: attempt.chainId,
                outcome: .terminalSucceeded,
                now: Date(timeIntervalSince1970: 1_002)
            )
            try journal.markCancellationConfirmed(
                reservationUUID: "reservation",
                now: Date(timeIntervalSince1970: 1_003)
            )
            let current = try journal.recoveryState().findAttemptForTest(attempt.occurrenceId)!
            assert(current.currentReservation.status == .cancellationRequired)
            assert(current.currentReservation.cancellationRequired)
            assert(current.currentReservation.cancellationConfirmedAtEpochMillis == nil)
        }
    }

    static func `지난_due는_새_claim을_만들지_않고_expired로_남긴다`() throws {
        try withJournal(ids: ["root", "reservation", "retry-token"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let attempt = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            let claim = try journal.claimNextReservation(
                occurrenceId: attempt.occurrenceId,
                operationId: "late",
                executionEpoch: "epoch",
                now: Date(timeIntervalSince1970: 1_700)
            )
            let current = try journal.recoveryState().findAttemptForTest(attempt.occurrenceId)!
            assert(claim == nil)
            assert(current.outcome == .expired)
            assert(current.currentReservation.operationId == nil)
            assert(current.currentReservation.status == .pending)
        }
    }

    static func `재울림은_부모_스냅샷을_강제로_채택한다`() throws {
        try withJournal(ids: ["root", "r1", "t1"]) { _, journal in
            let rootSnapshot = testSnapshot(sourceAlarmId: "root-source", scheduleVersion: 11, owner: .uncaptured)
            try enable(rootSnapshot, in: journal)
            let root = try journal.acceptStop(
                snapshot: rootSnapshot,
                occurrenceToken: "root-token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            let divergent = testSnapshot(
                sourceAlarmId: "other-source",
                scheduleVersion: 99,
                owner: .capturedAccount("wrong-owner")
            )
            let retry = try journal.acceptStop(
                snapshot: divergent,
                occurrenceToken: root.nextOccurrenceToken,
                parentOccurrenceId: root.occurrenceId,
                stopAt: Date(timeIntervalSince1970: 1_060)
            ).attempt
            assert(retry.snapshot == rootSnapshot)
            assert(retry.sourceKey == root.sourceKey)
        }
    }

    static func `잘못된_재울림_토큰과_terminal_parent는_거절한다`() throws {
        try withJournal(ids: ["root", "r1", "t1"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let root = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "root-token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            do {
                _ = try journal.acceptStop(
                    snapshot: root.snapshot,
                    occurrenceToken: "wrong-token",
                    parentOccurrenceId: root.occurrenceId,
                    stopAt: Date(timeIntervalSince1970: 1_060)
                )
                fatalError("wrong retry token must throw")
            } catch AlarmRecoveryJournalError.retryTokenMismatch {}
            try journal.markTerminal(
                chainId: root.chainId,
                outcome: .terminalForcedStop,
                now: Date(timeIntervalSince1970: 1_001)
            )
            do {
                _ = try journal.acceptStop(
                    snapshot: root.snapshot,
                    occurrenceToken: root.nextOccurrenceToken,
                    parentOccurrenceId: root.occurrenceId,
                    stopAt: Date(timeIntervalSince1970: 1_060)
                )
                fatalError("terminal parent must throw")
            } catch AlarmRecoveryJournalError.terminalChain {}
        }
    }

    static func `비활성화_뒤_지연된_중복_루트는_복원된다`() throws {
        try withJournal(ids: ["root", "reservation", "retry-token"]) { _, journal in
            try enable(testSnapshot(), in: journal)
            let first = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "root-token",
                stopAt: Date(timeIntervalSince1970: 1_000)
            ).attempt
            try journal.setSourceEligibility(sourceAlarmId: "alarm", scheduleVersion: 1, eligibility: .disabled)
            let duplicate = try journal.acceptStop(
                snapshot: testSnapshot(),
                occurrenceToken: "root-token",
                stopAt: Date(timeIntervalSince1970: 1_005)
            )
            assert(duplicate.duplicate)
            assert(duplicate.attempt == first)
        }
    }

    static func `동시_reader_writer가_손상_없이_끝난다`() throws {
        try withJournal { directory, journal in
            let writers = (0..<6).map { "rw-token-\($0)" }
            let writerProcesses = try writers.map { token -> Process in
                let process = Process()
                process.executableURL = URL(fileURLWithPath: CommandLine.arguments[0])
                process.arguments = ["child", directory.path, "alarm", token]
                try process.run()
                return process
            }
            let readerProcesses = try (0..<3).map { _ -> Process in
                let process = Process()
                process.executableURL = URL(fileURLWithPath: CommandLine.arguments[0])
                process.arguments = ["reader", directory.path, "20"]
                try process.run()
                return process
            }
            for process in writerProcesses + readerProcesses {
                process.waitUntilExit()
                assert(process.terminationStatus == 0)
            }
            let attempts = try journal.attemptsForRecovery()
            assert(attempts.count == 6)
            assert(Set(attempts.map(\.occurrenceId)).count == 6)
        }
    }
}

private extension AlarmRecoveryState {
    func findAttemptForTest(_ occurrenceId: String) -> AlarmRecoveryAttempt? {
        chains.values.flatMap(\.attempts).first { $0.occurrenceId == occurrenceId }
    }
}
