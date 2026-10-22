import AlarmKit
import Foundation
import SwiftUI

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
        }
    )

    static func dismiss(
        alarmId: String, occurrenceToken: String?, retryAttempt: Int,
        scheduleVersion: Int64, systemAlarmId: String, chainId: String?
    ) async -> AlarmRecoveryDismissOutcome {
        await command.dismiss(alarmId: alarmId, occurrenceToken: occurrenceToken,
            retryAttempt: retryAttempt, scheduleVersion: scheduleVersion,
            systemAlarmId: systemAlarmId, chainId: chainId)
    }

    @discardableResult
    static func stop(
        alarmId: String, occurrenceToken: String?, retryAttempt: Int = 0,
        scheduleVersion: Int64, systemAlarmId: String, now: Date = Date()
    ) async throws -> AlarmRecoveryStopDisposition {
        try await command.stop(alarmId: alarmId, occurrenceToken: occurrenceToken,
            retryAttempt: retryAttempt, scheduleVersion: scheduleVersion,
            systemAlarmId: systemAlarmId, now: now)
    }

    static func recover() async throws { try await command.recover() }

    @MainActor
    private static func schedule(_ attempt: AlarmRecoveryAttempt) async throws {
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
            return .notFound
        }
        try AlarmManager.shared.stop(id: id)
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
