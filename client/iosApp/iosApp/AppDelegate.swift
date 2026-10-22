import FirebaseCore
import FirebaseMessaging
import OSLog
import UIKit
import UserNotifications

private struct NotificationResponseCompletion: @unchecked Sendable {
    private let action: () -> Void

    init(_ action: @escaping () -> Void) {
        self.action = action
    }

    func call() {
        action()
    }
}

@MainActor
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        FirebaseMessagingAdapter.shared.updateToken(Messaging.messaging().fcmToken)
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // FirebaseAppDelegateProxyEnabled is NO, so Firebase cannot map this automatically.
        Messaging.messaging().apnsToken = deviceToken
        FirebaseMessagingAdapter.shared.refreshToken()
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        Logger(subsystem: Bundle.main.bundleIdentifier ?? "Ringout", category: "Push")
            .error("APNs registration failed.")
    }
}

extension AppDelegate: MessagingDelegate {
    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        Task { @MainActor in
            FirebaseMessagingAdapter.shared.updateToken(fcmToken)
        }
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        Messaging.messaging().appDidReceiveMessage(notification.request.content.userInfo)
        completionHandler([.list, .banner, .sound])
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        Messaging.messaging().appDidReceiveMessage(userInfo)
        let actionIdentifier = response.actionIdentifier
        let request = response.notification.request
        let notificationIdentifier = request.identifier
        let isRemoteNotification = request.trigger is UNPushNotificationTrigger
        let roomId = userInfo["roomId"] as? String
        let activityDate = userInfo["activityDate"] as? String
        let completion = NotificationResponseCompletion(completionHandler)
        Task { @MainActor in
            defer { completion.call() }
            guard actionIdentifier == UNNotificationDefaultActionIdentifier,
                  isRemoteNotification else { return }
            FirebaseMessagingAdapter.shared.receiveRoomActivityPush(
                notificationIdentifier: notificationIdentifier,
                roomId: roomId,
                activityDate: activityDate
            )
        }
    }
}
