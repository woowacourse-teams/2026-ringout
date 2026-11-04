import AlarmKit
import Foundation
import SwiftUI
import Shared

private struct RecoveryAlarmMetadata: AlarmMetadata {
    let sourceAlarmId: String
    let occurrenceToken: String
}

/// The only scheduler for journal-backed retries. No Room, DI or foreground
/// construction is needed between a stop and its durable next reservation.
enum AlarmRecoveryDriver {
    static let journal = AlarmRecoveryJournal()

    private static let command = AlarmRecoveryCommand(
        journal: journal,
        stopAlarm: { rawId in try await stopAlarm(rawId) },
        schedule: { attempt in try await schedule(attempt) },
        contains: { rawId in try await contains(rawId) },
        cancel: { rawId in try await cancelAlarm(rawId) },
        observation: { rawId, now in
            guard let value = try? AlarmRingingOccurrenceStore.shared.observation(systemAlarmId: rawId, now: now) else { return nil }
            return AlarmRecoveryStopObservation(occurrenceId: value.occurrenceId,
                ownerCaptured: value.ownerCaptured == true, ownerAccountId: value.ownerAccountId)
        },
        missionAccess: { try await restoreMissionAccess() }
    )

    static func dismiss(
        alarmId: String, occurrenceToken: String?, retryAttempt: Int,
        scheduleVersion: Int64, systemAlarmId: String, chainId: String?
    ) async -> AlarmRecoveryDismissOutcome {
        do {
            if try await restoreMissionAccess() == false {
                return await command.stopOnly(systemAlarmId: systemAlarmId)
            }
        } catch {
            return AlarmRecoveryDismissOutcome(osStopCode: .notAttempted, disposition: .failed,
                state: nil, occurrenceId: nil, message: error.localizedDescription)
        }
        return await command.dismiss(alarmId: alarmId, occurrenceToken: occurrenceToken,
            retryAttempt: retryAttempt, scheduleVersion: scheduleVersion,
            systemAlarmId: systemAlarmId, chainId: chainId)
    }

    static func stopOnly(systemAlarmId: String) async -> AlarmRecoveryDismissOutcome {
        await command.stopOnly(systemAlarmId: systemAlarmId)
    }

    private static func restoreMissionAccess() async throws -> Bool {
        try await withCheckedThrowingContinuation { continuation in
            IosAlarmAuthentication.shared.restoreMissionAccess { result in
                if result.restored {
                    continuation.resume(returning: result.missionAllowed)
                } else {
                    continuation.resume(throwing: NSError(domain: "RingoutAlarmAuthentication", code: 1,
                        userInfo: [NSLocalizedDescriptionKey: result.message ?? "인증 상태를 복원하지 못했습니다."]))
                }
            }
        }
    }

    @discardableResult
    static func stop(
        alarmId: String, occurrenceToken: String?, retryAttempt: Int = 0,
        scheduleVersion: Int64, systemAlarmId: String, now: Date = Date()
    ) async throws -> AlarmRecoveryStopDisposition {
        if try await restoreMissionAccess() == false {
            let result = await command.stopOnly(systemAlarmId: systemAlarmId)
            if result.disposition == .failed {
                throw NSError(domain: "RingoutAlarmStop", code: 1,
                    userInfo: [NSLocalizedDescriptionKey: result.message ?? "알람을 종료하지 못했습니다."])
            }
            return .preserved
        }
        return try await command.stop(alarmId: alarmId, occurrenceToken: occurrenceToken,
            retryAttempt: retryAttempt, scheduleVersion: scheduleVersion,
            systemAlarmId: systemAlarmId, now: now)
    }

    static func recover() async throws {
        try await reconcileDeferredLogoutCancellations()
        try await command.recover()
    }

    @MainActor
    private static func reconcileDeferredLogoutCancellations() throws {
        let ids = UserDefaults.standard.stringArray(forKey: "ringout.alarm.logout_cancel_after_stop") ?? []
        let alerting = Set(try AlarmManager.shared.alarms.filter { $0.state == .alerting }.map { $0.id.uuidString })
        for rawId in ids where !alerting.contains(rawId) {
            if let id = UUID(uuidString: rawId) { try cancelFutureAfterStopIfNeeded(id) }
        }
    }

    @MainActor
    static func suppressFutureReservationsForLogout() async throws {
        let alertingIds = Set(try AlarmManager.shared.alarms.filter { $0.state == .alerting }.map { $0.id.uuidString })
        let deferred = UserDefaults.standard.stringArray(forKey: "ringout.alarm.logout_cancel_after_stop") ?? []
        UserDefaults.standard.set(Array(Set(deferred).union(alertingIds)), forKey: "ringout.alarm.logout_cancel_after_stop")
        try journal.suppressFutureReservationsForLogout(excluding: alertingIds)
        try await command.recover()
    }

    @MainActor
    static func cancelFutureAfterStopIfNeeded(_ id: UUID) throws {
        var deferred = UserDefaults.standard.stringArray(forKey: "ringout.alarm.logout_cancel_after_stop") ?? []
        guard deferred.contains(id.uuidString) else { return }
        if try AlarmManager.shared.alarms.contains(where: { $0.id == id }) {
            try AlarmManager.shared.cancel(id: id)
        }
        deferred.removeAll { $0 == id.uuidString }
        UserDefaults.standard.set(deferred, forKey: "ringout.alarm.logout_cancel_after_stop")
    }

    static func allowNewSourceSchedule(_ id: UUID) {
        var deferred = UserDefaults.standard.stringArray(forKey: "ringout.alarm.logout_cancel_after_stop") ?? []
        deferred.removeAll { $0 == id.uuidString }
        UserDefaults.standard.set(deferred, forKey: "ringout.alarm.logout_cancel_after_stop")
    }

    @MainActor
    private static func schedule(_ attempt: AlarmRecoveryAttempt) async throws {
        let logoutGeneration = UserDefaults.standard.integer(forKey: "ringout.alarm.logout_generation")
        guard !UserDefaults.standard.bool(forKey: "ringout.alarm.explicit_logout_blocked") else {
            throw AlarmMissionEventInboxError.invalidAlarmId
        }
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
        if UserDefaults.standard.bool(forKey: "ringout.alarm.explicit_logout_blocked") ||
            logoutGeneration != UserDefaults.standard.integer(forKey: "ringout.alarm.logout_generation") {
            try AlarmManager.shared.cancel(id: id)
        }
    }

    @MainActor
    private static func contains(_ rawId: String) throws -> Bool {
        guard let id = UUID(uuidString: rawId) else { return false }
        return try AlarmManager.shared.alarms.contains { $0.id == id }
    }

    @MainActor
    private static func stopAlarm(_ rawId: String) throws -> AlarmRecoveryOSStopCode {
        guard let id = UUID(uuidString: rawId) else { return .invalidId }
        guard try AlarmManager.shared.alarms.contains(where: { $0.id == id && $0.state == .alerting }) else {
            try cancelFutureAfterStopIfNeeded(id)
            return .notFound
        }
        try AlarmManager.shared.stop(id: id)
        try cancelFutureAfterStopIfNeeded(id)
        return .success
    }

    @MainActor
    private static func cancelAlarm(_ rawId: String) throws {
        guard let id = UUID(uuidString: rawId) else { return }
        if try AlarmManager.shared.alarms.contains(where: { $0.id == id }) {
            try AlarmManager.shared.cancel(id: id)
        }
    }
}
