import CoreLocation
import Foundation
import Shared
import UIKit

/// Requests authorization without starting destination lookup or mission tracking.
@MainActor
final class OnboardingPermissionAdapter: NSObject,
    @preconcurrency IosOnboardingPermissionService,
    @preconcurrency CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var pendingPermission: OnboardingPermission?
    private var callbacks: [(OnboardingPermissionStatus) -> Void] = []
    private var isWaitingForSettings = false
    private var didLeaveForSettings = false

    override init() {
        super.init()
        manager.delegate = self
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(applicationWillResignActive),
            name: UIApplication.willResignActiveNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(applicationDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    func refresh(callback: @escaping (OnboardingPermissionState) -> Void) {
        Task { @MainActor in
            let notifications = await FirebaseMessagingAdapter.shared.notificationPermissionStatus()
            callback(OnboardingPermissionState(location: locationStatus, notifications: notifications))
        }
    }

    func request(
        permission: OnboardingPermission,
        callback: @escaping (OnboardingPermissionStatus) -> Void
    ) {
        if let pendingPermission {
            if pendingPermission == permission {
                callbacks.append(callback)
            } else {
                callback(.unavailable)
            }
            return
        }
        pendingPermission = permission
        callbacks = [callback]

        if permission == .location {
            switch locationStatus {
            case .requestable:
                manager.requestWhenInUseAuthorization()
            case .settingsrequired, .restricted, .servicesdisabled:
                openSettings()
            default:
                finish(locationStatus)
            }
        } else {
            Task { @MainActor in
                let status = await FirebaseMessagingAdapter.shared.notificationPermissionStatus()
                if status == .settingsrequired {
                    openSettings()
                } else if status == .requestable || status == .granted {
                    FirebaseMessagingAdapter.shared.requestNotificationPermissionIfNeeded { [weak self] result in
                        self?.finish(result)
                    }
                } else {
                    finish(status)
                }
            }
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard pendingPermission == .location, !isWaitingForSettings else { return }
        guard manager.authorizationStatus != .notDetermined else { return }
        finish(locationStatus)
    }

    private var locationStatus: OnboardingPermissionStatus {
        guard CLLocationManager.locationServicesEnabled() else { return .servicesdisabled }
        switch manager.authorizationStatus {
        case .notDetermined: return .requestable
        case .authorizedWhenInUse, .authorizedAlways:
            return manager.accuracyAuthorization == .fullAccuracy ? .granted : .settingsrequired
        case .denied: return .settingsrequired
        case .restricted: return .restricted
        @unknown default: return .unavailable
        }
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            finish(.unavailable)
            return
        }
        isWaitingForSettings = true
        didLeaveForSettings = false
        UIApplication.shared.open(url, options: [:]) { [weak self] opened in
            guard !opened else { return }
            Task { @MainActor in self?.finish(.unavailable) }
        }
    }

    @objc private func applicationWillResignActive() {
        if isWaitingForSettings {
            didLeaveForSettings = true
        }
    }

    @objc private func applicationDidBecomeActive() {
        guard isWaitingForSettings, didLeaveForSettings, let permission = pendingPermission else { return }
        didLeaveForSettings = false
        refresh { [weak self] state in
            guard let self else { return }
            if permission == .notifications, state.notifications == .granted {
                FirebaseMessagingAdapter.shared.requestNotificationPermissionIfNeeded { [weak self] status in
                    self?.finish(status)
                }
            } else {
                finish(permission == .location ? state.location : state.notifications)
            }
        }
    }

    private func finish(_ status: OnboardingPermissionStatus) {
        let completions = callbacks
        callbacks.removeAll()
        pendingPermission = nil
        isWaitingForSettings = false
        didLeaveForSettings = false
        completions.forEach { $0(status) }
    }
}
