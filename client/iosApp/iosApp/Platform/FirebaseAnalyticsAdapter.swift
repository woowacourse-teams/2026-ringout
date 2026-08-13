import FirebaseAnalytics
import Shared

final class FirebaseAnalyticsAdapter: IosAnalyticsTracker {
    func log(event: IosAnalyticsEventDto) {
        var parameters: [String: Any] = [:]
        event.parameters.forEach { parameter in
            if let textValue = parameter.textValue {
                parameters[parameter.name] = textValue
            } else if let numberValue = parameter.numberValue {
                parameters[parameter.name] = numberValue
            }
        }
        Analytics.logEvent(
            event.name,
            parameters: parameters.isEmpty ? nil : parameters
        )
    }
}
