import CoreLocation
import Foundation
import Shared
import UIKit

@MainActor
final class MissionLocationAdapter: NSObject,
    @MainActor IosMissionLocationService,
    @MainActor CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private weak var listener: IosMissionLocationListener?
    private var occurrenceId: String?
    private var revision: Int64 = 0
    private var backgroundRecoveryTask: UIBackgroundTaskIdentifier = .invalid
    private var backgroundRecoveryIds: Set<String> = []
    private var didRequestAlwaysAuthorization = false
    private var alwaysRequestDidNotUpgrade = false
    private var alwaysAuthorizationRequestFailed = false
    private var alwaysAuthorizationSession: CLServiceSession?
    private var alwaysAuthorizationDiagnosticsTask: Task<Void, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 5
        manager.activityType = .fitness
        manager.pausesLocationUpdatesAutomatically = false
    }

    deinit {
        alwaysAuthorizationDiagnosticsTask?.cancel()
        alwaysAuthorizationSession?.invalidate()
    }

    func currentState() -> MissionLocationState {
        MissionLocationState(
            services: CLLocationManager.locationServicesEnabled() ? .enabled : .disabled,
            authorization: authorizationState,
            accuracy: accuracyState,
            isTracking: occurrenceId != nil,
            isAwaitingAlwaysAuthorizationResult: didRequestAlwaysAuthorization,
            alwaysRequestDidNotUpgrade: alwaysRequestDidNotUpgrade,
            alwaysAuthorizationRequestFailed: alwaysAuthorizationRequestFailed,
            revision: revision
        )
    }

    func setListener(listener: IosMissionLocationListener?) {
        self.listener = listener
        emitState()
    }

    func requestWhenInUseAuthorization() {
        guard manager.authorizationStatus == .notDetermined else {
            emitState()
            return
        }
        manager.requestWhenInUseAuthorization()
    }

    func requestAlwaysAuthorization() {
        guard
            manager.authorizationStatus == .authorizedWhenInUse,
            !didRequestAlwaysAuthorization
        else {
            emitState()
            return
        }
        didRequestAlwaysAuthorization = true
        alwaysRequestDidNotUpgrade = false
        alwaysAuthorizationRequestFailed = false
        let session = CLServiceSession(authorization: .always)
        alwaysAuthorizationSession = session
        emitState()
        alwaysAuthorizationDiagnosticsTask = Task { @MainActor [weak self, session] in
            do {
                for try await diagnostic in session.diagnostics {
                    guard
                        let self,
                        self.alwaysAuthorizationSession === session
                    else {
                        return
                    }
                    if diagnostic.authorizationRequestInProgress {
                        continue
                    }
                    if diagnostic.alwaysAuthorizationDenied ||
                        self.manager.authorizationStatus == .authorizedAlways {
                        self.finishAlwaysAuthorizationRequest()
                        return
                    }
                }
            } catch is CancellationError {
                return
            } catch {
                guard let self, self.alwaysAuthorizationSession === session else {
                    return
                }
                self.failAlwaysAuthorizationRequest()
            }
        }
    }

    func confirmAlwaysAuthorizationResult() {
        if alwaysAuthorizationRequestFailed {
            alwaysAuthorizationRequestFailed = false
            emitState()
            return
        }
        finishAlwaysAuthorizationRequest()
    }

    func requestTemporaryFullAccuracyAuthorization(purposeKey: String) {
        guard manager.accuracyAuthorization == .reducedAccuracy else {
            emitState()
            return
        }
        manager.requestTemporaryFullAccuracyAuthorization(withPurposeKey: purposeKey) {
            [weak self] _ in
            Task { @MainActor [weak self] in
                self?.emitState()
            }
        }
    }

    func startTracking(occurrenceId: String) {
        guard CLLocationManager.locationServicesEnabled() else {
            emitError("위치 서비스가 꺼져 있습니다.")
            return
        }
        guard
            manager.authorizationStatus == .authorizedAlways ||
            manager.authorizationStatus == .authorizedWhenInUse
        else {
            emitState()
            return
        }
        self.occurrenceId = occurrenceId
        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
        manager.startUpdatingLocation()
        emitState()
    }

    func stopTracking() {
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
        manager.showsBackgroundLocationIndicator = false
        occurrenceId = nil
        emitState()
    }

    func beginBackgroundRecovery(recoveryId: String) {
        let wasInserted = backgroundRecoveryIds.insert(recoveryId).inserted
        guard wasInserted, backgroundRecoveryTask == .invalid else { return }
        backgroundRecoveryTask = UIApplication.shared.beginBackgroundTask(
            withName: "RingoutMissionRecovery"
        ) { [weak self] in
            Task { @MainActor [weak self] in self?.expireBackgroundRecovery() }
        }
    }

    func endBackgroundRecovery(recoveryId: String) {
        backgroundRecoveryIds.remove(recoveryId)
        guard backgroundRecoveryIds.isEmpty else { return }
        endBackgroundRecoveryTask()
    }

    private func expireBackgroundRecovery() {
        backgroundRecoveryIds.removeAll()
        endBackgroundRecoveryTask()
    }

    private func endBackgroundRecoveryTask() {
        guard backgroundRecoveryTask != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundRecoveryTask)
        backgroundRecoveryTask = .invalid
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if didRequestAlwaysAuthorization {
            switch manager.authorizationStatus {
            case .denied, .restricted:
                finishAlwaysAuthorizationRequest()
                return
            case .notDetermined, .authorizedWhenInUse, .authorizedAlways:
                break
            @unknown default:
                finishAlwaysAuthorizationRequest()
                return
            }
        }
        emitState()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let occurrenceId else { return }
        let receivedAt = Date()
        let receivedAtUptime = ProcessInfo.processInfo.systemUptime
        for location in locations where location.horizontalAccuracy >= 0 {
            let capturedAtEpochMillis = Int64(location.timestamp.timeIntervalSince1970 * 1_000)
            let fixAgeSeconds = receivedAt.timeIntervalSince(location.timestamp)
            let fixUptimeSeconds = receivedAtUptime - fixAgeSeconds
            let elapsedRealtimeNanos = Int64(fixUptimeSeconds * 1_000_000_000)
            listener?.onLocation(
                occurrenceId: occurrenceId,
                location: ActiveAlarmMissionLocation(
                    latitude: location.coordinate.latitude,
                    longitude: location.coordinate.longitude,
                    accuracyMeters: Float(location.horizontalAccuracy),
                    capturedAtEpochMillis: capturedAtEpochMillis
                ),
                elapsedRealtimeNanos: elapsedRealtimeNanos
            )
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        emitError(error.localizedDescription)
    }

    private var authorizationState: MissionLocationAuthorizationState {
        switch manager.authorizationStatus {
        case .notDetermined: return .notDetermined
        case .authorizedWhenInUse: return .whenInUse
        case .authorizedAlways: return .always
        case .denied: return .denied
        case .restricted: return .restricted
        @unknown default: return .restricted
        }
    }

    private var accuracyState: MissionLocationAccuracyState {
        switch manager.accuracyAuthorization {
        case .fullAccuracy: return .full
        case .reducedAccuracy: return .reduced
        @unknown default: return .unknown
        }
    }

    private func emitState() {
        revision += 1
        listener?.onStateChanged(state: currentState())
    }

    private func finishAlwaysAuthorizationRequest() {
        guard didRequestAlwaysAuthorization else {
            emitState()
            return
        }
        didRequestAlwaysAuthorization = false
        alwaysAuthorizationRequestFailed = false
        alwaysRequestDidNotUpgrade =
            manager.authorizationStatus == .authorizedWhenInUse
        clearAlwaysAuthorizationSession()
        emitState()
    }

    private func failAlwaysAuthorizationRequest() {
        didRequestAlwaysAuthorization = false
        alwaysRequestDidNotUpgrade = false
        alwaysAuthorizationRequestFailed = true
        clearAlwaysAuthorizationSession()
        emitState()
    }

    private func clearAlwaysAuthorizationSession() {
        alwaysAuthorizationDiagnosticsTask?.cancel()
        alwaysAuthorizationDiagnosticsTask = nil
        alwaysAuthorizationSession?.invalidate()
        alwaysAuthorizationSession = nil
    }

    private func emitError(_ message: String) {
        listener?.onError(message: message)
        emitState()
    }

}
