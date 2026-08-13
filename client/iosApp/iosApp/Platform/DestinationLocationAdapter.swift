import CoreLocation
import Foundation
import Shared

final class DestinationLocationAdapter: NSObject, IosDestinationLocationService, CLLocationManagerDelegate {
    private struct ActiveRequest {
        let requestId: Int32
        let callback: IosDestinationLocationCallback
    }

    private let locationTimeoutSeconds: TimeInterval = 15
    private var locationManager: CLLocationManager?
    private var activeRequest: ActiveRequest?
    private var timeoutWorkItem: DispatchWorkItem?

    func request(requestId: Int32, callback: IosDestinationLocationCallback) {
        runOnMain { [weak self] in
            self?.startRequest(requestId: requestId, callback: callback)
        }
    }

    func cancel(requestId: Int32) {
        runOnMain { [weak self] in
            self?.cancelActiveRequest(requestId: requestId)
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        runOnMain { [weak self] in
            self?.continueRequestAfterAuthorizationChange(manager)
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        runOnMain { [weak self] in
            guard let self, let activeRequest else { return }
            guard manager === locationManager else { return }
            guard let location = locations.last else {
                finishWithError(.unavailable, requestId: activeRequest.requestId)
                return
            }

            guard hasFullAccuracy(manager) else {
                finishWithError(.reducedAccuracy, requestId: activeRequest.requestId)
                return
            }

            emitLocation(location, requestId: activeRequest.requestId, isFinal: true)
            finishRequestIfCurrent(activeRequest.requestId)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        runOnMain { [weak self] in
            guard let self, let activeRequest else { return }
            guard manager === locationManager else { return }
            finishWithError(.unavailable, requestId: activeRequest.requestId)
        }
    }

    private func startRequest(requestId: Int32, callback: IosDestinationLocationCallback) {
        stopLocationUpdates()
        activeRequest = ActiveRequest(requestId: requestId, callback: callback)

        let manager = configuredLocationManager()
        guard CLLocationManager.locationServicesEnabled() else {
            finishWithError(.servicesDisabled, requestId: requestId)
            return
        }

        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            requestAuthorizedLocation(manager, requestId: requestId)
        case .denied:
            finishWithError(.permissionDenied, requestId: requestId)
        case .restricted:
            finishWithError(.restricted, requestId: requestId)
        @unknown default:
            finishWithError(.unavailable, requestId: requestId)
        }
    }

    private func continueRequestAfterAuthorizationChange(_ manager: CLLocationManager) {
        guard let activeRequest else { return }
        guard manager === locationManager else { return }

        guard CLLocationManager.locationServicesEnabled() else {
            finishWithError(.servicesDisabled, requestId: activeRequest.requestId)
            return
        }

        switch manager.authorizationStatus {
        case .notDetermined:
            return
        case .authorizedWhenInUse, .authorizedAlways:
            requestAuthorizedLocation(manager, requestId: activeRequest.requestId)
        case .denied:
            finishWithError(.permissionDenied, requestId: activeRequest.requestId)
        case .restricted:
            finishWithError(.restricted, requestId: activeRequest.requestId)
        @unknown default:
            finishWithError(.unavailable, requestId: activeRequest.requestId)
        }
    }

    private func requestAuthorizedLocation(_ manager: CLLocationManager, requestId: Int32) {
        guard isCurrentRequest(requestId) else { return }
        guard hasFullAccuracy(manager) else {
            finishWithError(.reducedAccuracy, requestId: requestId)
            return
        }

        if let cachedLocation = manager.location, cachedLocation.horizontalAccuracy >= 0 {
            emitLocation(cachedLocation, requestId: requestId, isFinal: false)
        }

        scheduleTimeout(for: requestId)
        manager.requestLocation()
    }

    private func configuredLocationManager() -> CLLocationManager {
        if let locationManager {
            return locationManager
        }

        let manager = CLLocationManager()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
        locationManager = manager
        return manager
    }

    private func emitLocation(_ location: CLLocation, requestId: Int32, isFinal: Bool) {
        guard isCurrentRequest(requestId), let callback = activeRequest?.callback else { return }

        let timestampEpochMillis = Int64(location.timestamp.timeIntervalSince1970 * 1_000)
        let destinationLocation = IosDestinationLocation(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            timestampEpochMillis: timestampEpochMillis,
            horizontalAccuracyMeters: Float(location.horizontalAccuracy),
            hasFullAccuracy: hasFullAccuracy(locationManager)
        )
        callback.onLocation(requestId: requestId, location: destinationLocation, isFinal: isFinal)
    }

    private func finishWithError(_ error: IosDestinationLocationError, requestId: Int32) {
        guard isCurrentRequest(requestId), let callback = activeRequest?.callback else { return }
        callback.onError(requestId: requestId, error: error)
        finishRequestIfCurrent(requestId)
    }

    private func cancelActiveRequest(requestId: Int32) {
        guard isCurrentRequest(requestId), let callback = activeRequest?.callback else { return }
        callback.onCancelled(requestId: requestId)
        finishRequestIfCurrent(requestId)
    }

    private func finishRequestIfCurrent(_ requestId: Int32) {
        guard isCurrentRequest(requestId) else { return }
        stopLocationUpdates()
        activeRequest = nil
    }

    private func stopLocationUpdates() {
        timeoutWorkItem?.cancel()
        timeoutWorkItem = nil
        locationManager?.stopUpdatingLocation()
    }

    private func scheduleTimeout(for requestId: Int32) {
        timeoutWorkItem?.cancel()

        let workItem = DispatchWorkItem { [weak self] in
            self?.runOnMain { [weak self] in
                guard let self, isCurrentRequest(requestId) else { return }
                finishWithError(.unavailable, requestId: requestId)
            }
        }
        timeoutWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + locationTimeoutSeconds, execute: workItem)
    }

    private func isCurrentRequest(_ requestId: Int32) -> Bool {
        activeRequest?.requestId == requestId
    }

    private func hasFullAccuracy(_ manager: CLLocationManager?) -> Bool {
        guard let manager else { return false }
        if #available(iOS 14.0, *) {
            return manager.accuracyAuthorization == .fullAccuracy
        }
        return true
    }

    private func runOnMain(_ work: @escaping () -> Void) {
        if Thread.isMainThread {
            work()
        } else {
            DispatchQueue.main.async(execute: work)
        }
    }
}
