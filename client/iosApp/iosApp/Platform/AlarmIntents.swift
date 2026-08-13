import AppIntents
import Foundation

struct StopAlarmIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "알람 중지"
    static var supportedModes: IntentModes = .background
    static var authenticationPolicy: IntentAuthenticationPolicy = .alwaysAllowed

    @Parameter(title: "Alarm ID")
    var alarmId: String

    init() {
        alarmId = ""
    }

    init(alarmId: String) {
        self.alarmId = alarmId
    }

    func perform() async throws -> some IntentResult {
        try RingoutAlarmMissionEventInbox.shared.record(
            alarmId: alarmId,
            action: .stop
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

    init() {
        alarmId = ""
    }

    init(alarmId: String) {
        self.alarmId = alarmId
    }

    func perform() async throws -> some IntentResult {
        try RingoutAlarmMissionEventInbox.shared.record(
            alarmId: alarmId,
            action: .open
        )
        return .result()
    }
}
