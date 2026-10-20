import FirebaseMessaging
import OSLog
import Shared
import UIKit
import UserNotifications

@MainActor
final class FirebaseMessagingAdapter {
    static let shared = FirebaseMessagingAdapter()

    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "Ringout", category: "Push")
    private var tokenListener: IosFcmTokenListener?
    private var permissionRequestInFlight = false
    private(set) var latestToken: String?

    private init() {}

    func observeToken(listener: IosFcmTokenListener) {
        tokenListener = listener
        listener.onTokenChanged(token: latestToken)
    }

    func updateToken(_ token: String?) {
        let token = token.flatMap { $0.isEmpty ? nil : $0 }
        guard latestToken != token else { return }
        latestToken = token
        tokenListener?.onTokenChanged(token: token)
    }

    func refreshToken() {
        Messaging.messaging().token { token, error in
            Task { @MainActor in
                if error != nil {
                    self.logger.error("FCM registration token lookup failed.")
                    return
                }
                self.updateToken(token)
            }
        }
    }

    func requestNotificationPermissionIfNeeded() {
        guard !permissionRequestInFlight else { return }
        permissionRequestInFlight = true
        Task {
            defer { permissionRequestInFlight = false }
            let center = UNUserNotificationCenter.current()
            let settings = await center.notificationSettings()
            switch settings.authorizationStatus {
            case .notDetermined:
                do {
                    let granted = try await center.requestAuthorization(options: [.alert, .badge, .sound])
                    if granted {
                        UIApplication.shared.registerForRemoteNotifications()
                    }
                } catch {
                    logger.error("Notification permission request failed.")
                }
            case .authorized, .provisional, .ephemeral:
                UIApplication.shared.registerForRemoteNotifications()
            case .denied:
                break
            @unknown default:
                break
            }
        }
    }
}
