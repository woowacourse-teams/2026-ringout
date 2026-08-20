import AppIntents
import AlarmKit
import Foundation

struct StopAlarmIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "알람 중지"
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

    init() {
        alarmId = ""
        occurrenceId = ""
        retryAttempt = 0
        systemAlarmId = ""
    }

    init(
        alarmId: String,
        occurrenceId: String = "",
        retryAttempt: Int = 0,
        systemAlarmId: String = ""
    ) {
        self.alarmId = alarmId
        self.occurrenceId = occurrenceId
        self.retryAttempt = retryAttempt
        self.systemAlarmId = systemAlarmId
    }

    func perform() async throws -> some IntentResult {
        try RingoutAlarmMissionEventInbox.shared.record(
            alarmId: alarmId,
            action: .stop,
            occurrenceId: occurrenceId.isEmpty ? nil : occurrenceId,
            retryAttempt: retryAttempt
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

    init() {
        alarmId = ""
        occurrenceId = ""
        retryAttempt = 0
        systemAlarmId = ""
    }

    init(
        alarmId: String,
        occurrenceId: String = "",
        retryAttempt: Int = 0,
        systemAlarmId: String = ""
    ) {
        self.alarmId = alarmId
        self.occurrenceId = occurrenceId
        self.retryAttempt = retryAttempt
        self.systemAlarmId = systemAlarmId
    }

    func perform() async throws -> some IntentResult {
        if let id = UUID(uuidString: systemAlarmId) {
            if try AlarmManager.shared.alarms.contains(where: { $0.id == id }) {
                try AlarmManager.shared.stop(id: id)
            }
        }
        try RingoutAlarmMissionEventInbox.shared.record(
            alarmId: alarmId,
            action: .open,
            occurrenceId: occurrenceId.isEmpty ? nil : occurrenceId,
            retryAttempt: retryAttempt
        )
        return .result()
    }
}
