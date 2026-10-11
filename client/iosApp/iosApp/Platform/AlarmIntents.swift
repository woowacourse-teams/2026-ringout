import AppIntents
import AlarmKit
import Foundation
import UIKit

struct StopAlarmIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "알람 중지"
    static var supportedModes: IntentModes = [.background, .foreground(.deferred)]
    static var authenticationPolicy: IntentAuthenticationPolicy = .alwaysAllowed

    @Parameter(title: "Alarm ID")
    var alarmId: String

    @Parameter(title: "Occurrence ID")
    var occurrenceId: String

    @Parameter(title: "Retry Attempt")
    var retryAttempt: Int

    @Parameter(title: "System Alarm ID")
    var systemAlarmId: String

    @Parameter(title: "Schedule Version")
    var scheduleVersion: Int

    init() {
        alarmId = ""
        occurrenceId = ""
        retryAttempt = 0
        systemAlarmId = ""
        scheduleVersion = 1
    }

    init(
        alarmId: String,
        occurrenceId: String = "",
        retryAttempt: Int = 0,
        systemAlarmId: String = "",
        scheduleVersion: Int = 1
    ) {
        self.alarmId = alarmId
        self.occurrenceId = occurrenceId
        self.retryAttempt = retryAttempt
        self.systemAlarmId = systemAlarmId
        self.scheduleVersion = scheduleVersion
    }

    func perform() async throws -> some IntentResult {
        let enteredAt = Date()
        if try AlarmRecoveryDriver.journal.sourceSnapshot(
            sourceAlarmId: alarmId, scheduleVersion: Int64(scheduleVersion)
        ) != nil {
            try await AlarmRecoveryDriver.stop(
                alarmId: alarmId,
                occurrenceToken: occurrenceId.isEmpty ? nil : occurrenceId,
                scheduleVersion: Int64(scheduleVersion),
                systemAlarmId: systemAlarmId,
                now: enteredAt
            )
            RingoutAlarmMissionEventInbox.shared.notifyRecoveryChanged()
        } else {
            // Existing OS configurations created before the snapshot migration.
            try RingoutAlarmMissionEventInbox.shared.record(
                alarmId: alarmId, action: .stop,
                occurrenceId: occurrenceId.isEmpty ? nil : occurrenceId,
                retryAttempt: retryAttempt, scheduleVersion: Int64(scheduleVersion),
                systemAlarmId: systemAlarmId, now: enteredAt
            )
        }
        // Deferred mode requests foreground after this background work completes.
        // No optional transition error is swallowed, and cancellation cannot undo
        // the durable stop or its already awaited reservation.
        let protectedDataAvailable = await MainActor.run { UIApplication.shared.isProtectedDataAvailable }
        try? AlarmRecoveryDriver.journal.recordPresentation(
            stage: "stop_committed_foreground_deferred",
            mode: systemContext.currentMode.debugDescription,
            canContinueInForeground: systemContext.currentMode.canContinueInForeground,
            protectedDataAvailable: protectedDataAvailable
        )
        return .result()
    }
}

struct OpenRingoutIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "링아웃 열기"
    static var supportedModes: IntentModes = .foreground(.immediate)
    static var authenticationPolicy: IntentAuthenticationPolicy = .alwaysAllowed

    @Parameter(title: "Alarm ID")
    var alarmId: String

    @Parameter(title: "Occurrence ID")
    var occurrenceId: String

    @Parameter(title: "Retry Attempt")
    var retryAttempt: Int

    @Parameter(title: "System Alarm ID")
    var systemAlarmId: String

    @Parameter(title: "Schedule Version")
    var scheduleVersion: Int

    init() {
        alarmId = ""
        occurrenceId = ""
        retryAttempt = 0
        systemAlarmId = ""
        scheduleVersion = 1
    }

    init(
        alarmId: String,
        occurrenceId: String = "",
        retryAttempt: Int = 0,
        systemAlarmId: String = "",
        scheduleVersion: Int = 1
    ) {
        self.alarmId = alarmId
        self.occurrenceId = occurrenceId
        self.retryAttempt = retryAttempt
        self.systemAlarmId = systemAlarmId
        self.scheduleVersion = scheduleVersion
    }

    func perform() async throws -> some IntentResult {
        var didStopRinging = false
        if let id = UUID(uuidString: systemAlarmId) {
            if try AlarmManager.shared.alarms.contains(where: { $0.id == id && $0.state == .alerting }) {
                try AlarmManager.shared.stop(id: id)
                didStopRinging = true
            }
        }
        if didStopRinging, try AlarmRecoveryDriver.journal.sourceSnapshot(
            sourceAlarmId: alarmId, scheduleVersion: Int64(scheduleVersion)
        ) != nil {
            try await AlarmRecoveryDriver.stop(
                alarmId: alarmId,
                occurrenceToken: occurrenceId.isEmpty ? nil : occurrenceId,
                scheduleVersion: Int64(scheduleVersion), systemAlarmId: systemAlarmId
            )
            RingoutAlarmMissionEventInbox.shared.notifyRecoveryChanged()
        } else {
            try RingoutAlarmMissionEventInbox.shared.record(
                alarmId: alarmId, action: .open,
                occurrenceId: occurrenceId.isEmpty ? nil : occurrenceId,
                retryAttempt: retryAttempt, scheduleVersion: Int64(scheduleVersion),
                systemAlarmId: systemAlarmId, didStopRinging: didStopRinging
            )
        }
        return .result()
    }
}
