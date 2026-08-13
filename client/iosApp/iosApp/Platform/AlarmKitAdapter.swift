import AlarmKit
import Foundation
import Shared

private struct RingoutAlarmMetadata: AlarmMetadata {
    let occurrenceId: String
}

final class AlarmKitAdapter {
    private typealias CompileSpikeConfiguration =
        AlarmManager.AlarmConfiguration<RingoutAlarmMetadata>

    func authorizationState() -> IosAlarmAuthorizationState {
        switch AlarmManager.shared.authorizationState {
        case .notDetermined:
            return .notDetermined
        case .denied:
            return .denied
        case .authorized:
            return .authorized
        @unknown default:
            return .notDetermined
        }
    }

    func normalizeAlarmId(_ id: String) -> String? {
        UUID(uuidString: id)?.uuidString
    }

    private static var compileSpikeConfigurationType: Any.Type {
        CompileSpikeConfiguration.self
    }
}
