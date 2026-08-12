import Foundation
import GoogleMaps
import GooglePlacesSwift

enum GoogleSdkComponentState {
    case notConfigured
    case configured
    case rejected
}

struct GoogleSdkConfiguration {
    let maps: GoogleSdkComponentState
    let places: GoogleSdkComponentState

    static let notConfigured = GoogleSdkConfiguration(
        maps: .notConfigured,
        places: .notConfigured
    )
}

enum GoogleSdkBootstrap {
    @MainActor
    static func configure(bundle: Bundle = .main) -> GoogleSdkConfiguration {
        guard let apiKey = configuredApiKey(in: bundle) else {
            return .notConfigured
        }

        return GoogleSdkConfiguration(
            maps: GMSServices.provideAPIKey(apiKey) ? .configured : .rejected,
            places: PlacesClient.provideAPIKey(apiKey) ? .configured : .rejected
        )
    }

    private static func configuredApiKey(in bundle: Bundle) -> String? {
        guard let rawValue = bundle.object(
            forInfoDictionaryKey: "GoogleIosSdkApiKey"
        ) as? String else {
            return nil
        }

        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard
            !value.isEmpty,
            !value.contains("$("),
            !value.contains("${")
        else {
            return nil
        }
        return value
    }
}
