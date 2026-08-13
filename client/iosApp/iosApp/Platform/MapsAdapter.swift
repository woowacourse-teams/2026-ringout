import GoogleMaps
import Shared
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

        let options = GMSMapViewOptions()
        options.camera = GMSCameraPosition.camera(
            withLatitude: DestinationMapDefaults.latitude,
            longitude: DestinationMapDefaults.longitude,
            zoom: DestinationMapDefaults.zoom
        )
        return GMSMapView(options: options)
    }

    @MainActor
    func makeDestinationMapController(
        initialLatitude: Double,
        initialLongitude: Double,
        listener: IosDestinationMapListener
    ) -> IosDestinationMapController? {
        guard isAvailable else { return nil }

        return DestinationMapController(
            initialLatitude: initialLatitude,
            initialLongitude: initialLongitude,
            listener: listener
        )
    }
}

@MainActor
private final class DestinationMapController: NSObject, @MainActor IosDestinationMapController, GMSMapViewDelegate {
    private let mapView: GMSMapView
    private let geocoder = GMSGeocoder()
    private var listener: IosDestinationMapListener?
    private var generation: Int64 = 0
    private var settledCoordinate: CLLocationCoordinate2D?
    private var lastCommandId: Int32?
    private var isDisposed = false

    init(
        initialLatitude: Double,
        initialLongitude: Double,
        listener: IosDestinationMapListener
    ) {
        let options = GMSMapViewOptions()
        options.camera = GMSCameraPosition.camera(
            withLatitude: initialLatitude,
            longitude: initialLongitude,
            zoom: DestinationMapDefaults.zoom
        )
        mapView = GMSMapView(options: options)
        self.listener = listener
        super.init()

        configureMapView()
    }

    func view() -> UIView {
        mapView
    }

    func setDarkModeEnabled(isEnabled: Bool) {
        mapView.overrideUserInterfaceStyle = isEnabled ? .dark : .light
    }

    func moveCamera(
        latitude: Double,
        longitude: Double,
        commandId: Int32
    ) {
        guard !isDisposed else { return }
        if let lastCommandId, commandId <= lastCommandId { return }

        lastCommandId = commandId
        invalidateSettlement()

        let camera = GMSCameraPosition.camera(
            withLatitude: latitude,
            longitude: longitude,
            zoom: DestinationMapDefaults.zoom
        )
        mapView.animate(to: camera)
    }

    func dispose() {
        isDisposed = true
        invalidateSettlement()
        mapView.delegate = nil
        listener = nil
    }

    nonisolated func mapView(
        _ mapView: GMSMapView,
        willMove gesture: Bool
    ) {
        Task { @MainActor [weak self] in
            self?.handleCameraMoveStarted(isGesture: gesture)
        }
    }

    nonisolated func mapView(
        _ mapView: GMSMapView,
        idleAt position: GMSCameraPosition
    ) {
        let latitude = position.target.latitude
        let longitude = position.target.longitude
        Task { @MainActor [weak self] in
            self?.handleCameraSettled(latitude: latitude, longitude: longitude)
        }
    }

    private func handleCameraMoveStarted(isGesture: Bool) {
        guard !isDisposed else { return }

        invalidateSettlement()
        listener?.onCameraMoveStarted(isGesture: isGesture)
    }

    private func handleCameraSettled(
        latitude: Double,
        longitude: Double
    ) {
        guard !isDisposed else { return }

        let coordinate = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        guard let settlementGeneration = registerSettlement(coordinate: coordinate) else {
            return
        }

        listener?.onCameraSettled(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude
        )
        reverseGeocode(coordinate: coordinate, generation: settlementGeneration)
    }

    private func configureMapView() {
        mapView.mapType = .normal
        mapView.delegate = self
        mapView.isMyLocationEnabled = false
        mapView.padding = UIEdgeInsets(
            top: DestinationMapDefaults.verticalPadding,
            left: DestinationMapDefaults.horizontalPadding,
            bottom: DestinationMapDefaults.verticalPadding,
            right: DestinationMapDefaults.horizontalPadding
        )

        let settings = mapView.settings
        settings.compassButton = true
        settings.indoorPicker = false
        settings.myLocationButton = false
        settings.scrollGestures = true
        settings.zoomGestures = true
        settings.rotateGestures = true
        settings.tiltGestures = false
    }

    private func invalidateSettlement() {
        generation += 1
        settledCoordinate = nil
    }

    private func registerSettlement(coordinate: CLLocationCoordinate2D) -> Int64? {
        if hasSameCoordinate(as: coordinate) { return nil }

        generation += 1
        settledCoordinate = coordinate
        return generation
    }

    private func isCurrentSettlement(
        generation expectedGeneration: Int64,
        coordinate: CLLocationCoordinate2D
    ) -> Bool {
        generation == expectedGeneration && hasSameCoordinate(as: coordinate)
    }

    private func hasSameCoordinate(as coordinate: CLLocationCoordinate2D) -> Bool {
        guard let settledCoordinate else { return false }

        return abs(settledCoordinate.latitude - coordinate.latitude) < DestinationMapDefaults.coordinateTolerance &&
            abs(settledCoordinate.longitude - coordinate.longitude) < DestinationMapDefaults.coordinateTolerance
    }

    private func reverseGeocode(
        coordinate: CLLocationCoordinate2D,
        generation settlementGeneration: Int64
    ) {
        geocoder.reverseGeocodeCoordinate(coordinate) { [weak self] response, _ in
            Task { @MainActor [weak self] in
                guard
                    let self,
                    !self.isDisposed,
                    self.isCurrentSettlement(
                        generation: settlementGeneration,
                        coordinate: coordinate
                    )
                else {
                    return
                }

                let address = response?.firstResult()
                self.listener?.onAddressResolved(
                    generation: settlementGeneration,
                    latitude: coordinate.latitude,
                    longitude: coordinate.longitude,
                    placeName: address?.thoroughfare,
                    address: address?.formattedAddress
                )
            }
        }
    }
}

private enum DestinationMapDefaults {
    static let latitude = 37.5665
    static let longitude = 126.9780
    static let zoom: Float = 17
    static let coordinateTolerance = 0.00001
    static let horizontalPadding: CGFloat = 16
    static let verticalPadding: CGFloat = 96
}

private extension GMSAddress {
    var formattedAddress: String? {
        let lines = self.lines ?? []
        let joined = lines
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        return joined.isEmpty ? nil : joined
    }
}
