import GoogleMaps
import UIKit

final class MapsAdapter {
    private let configurationState: GoogleSdkComponentState

    init(configurationState: GoogleSdkComponentState) {
        self.configurationState = configurationState
    }

    var isAvailable: Bool {
        configurationState == .configured
    }

    @MainActor
    func makeMapView() -> UIView? {
        guard isAvailable else { return nil }

        let mapView = GMSMapView()
        mapView.mapType = .normal
        return mapView
    }
}
