import FirebaseMessaging
import OSLog
import Shared
import UIKit
import UserNotifications

@MainActor
final class FirebaseMessagingAdapter {
    private struct PendingRoomActivityPush {
        let requestId: String
        let roomId: String?
        let activityDate: String?
    }

    static let shared = FirebaseMessagingAdapter()

    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "Ringout", category: "Push")
    private var tokenListener: IosFcmTokenListener?
    private var roomActivityPushObserver: (id: Int64, listener: IosRoomActivityPushListener)?
    private var nextRoomActivityPushObserverId: Int64 = 0
    private var pendingRoomActivityPush: PendingRoomActivityPush?
    private var handledNotificationIdentifiers = Set<String>()
    private var permissionRequestInFlight = false
    private(set) var latestToken: String?

    private init() {}

    func observeToken(listener: IosFcmTokenListener) {
        tokenListener = listener
        listener.onTokenChanged(token: latestToken)
    }

    func observeRoomActivityPush(listener: IosRoomActivityPushListener) -> Int64 {
        nextRoomActivityPushObserverId &+= 1
        let observerId = nextRoomActivityPushObserverId
        roomActivityPushObserver = (observerId, listener)
        if let pendingRoomActivityPush {
            deliver(pendingRoomActivityPush, to: listener)
        }
        return observerId
    }

    func removeRoomActivityPushObserver(observerId: Int64) {
        guard roomActivityPushObserver?.id == observerId else { return }
        roomActivityPushObserver = nil
    }

    func acknowledgeRoomActivityPush(requestId: String) {
        guard pendingRoomActivityPush?.requestId == requestId else { return }
        pendingRoomActivityPush = nil
    }

    func receiveRoomActivityPush(
        notificationIdentifier: String,
        roomId: String?,
        activityDate: String?
    ) {
        guard handledNotificationIdentifiers.insert(notificationIdentifier).inserted else { return }
        let request = PendingRoomActivityPush(
            requestId: UUID().uuidString,
            roomId: roomId,
            activityDate: activityDate
        )
        pendingRoomActivityPush = request
        if let observer = roomActivityPushObserver {
            deliver(request, to: observer.listener)
        }
    }

    private func deliver(_ request: PendingRoomActivityPush, to listener: IosRoomActivityPushListener) {
        listener.onRoomActivityPushRequest(
            requestId: request.requestId,
            roomId: request.roomId,
            activityDate: request.activityDate
        )
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
