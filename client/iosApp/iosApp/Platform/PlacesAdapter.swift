import GooglePlacesSwift

final class PlacesAdapter {
    private let configurationState: GoogleSdkComponentState

    init(configurationState: GoogleSdkComponentState) {
        self.configurationState = configurationState
    }

    var isAvailable: Bool {
        configurationState == .configured
    }

    @MainActor
    func makeClient() -> PlacesClient? {
        guard isAvailable else { return nil }
        return PlacesClient.shared
    }
}
