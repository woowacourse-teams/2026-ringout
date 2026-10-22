import Foundation
import OSLog
import UserNotifications
#if canImport(Shared)
import Shared
#endif

// Inject the OS boundary so tests never need a permission dialog or system notification.
@MainActor
protocol MissionArrivalNotificationCenter {
    func authorizationStatus() async -> UNAuthorizationStatus
    func add(_ request: UNNotificationRequest) async throws
}

@MainActor
private struct SystemMissionArrivalNotificationCenter: MissionArrivalNotificationCenter {
    func authorizationStatus() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    func add(_ request: UNNotificationRequest) async throws {
        try await UNUserNotificationCenter.current().add(request)
    }
}

enum MissionArrivalNotificationResult {
    case handled, permissionSkipped, alreadyHandled, missingChain, claimStorageFailed, requestFailed
}

@MainActor
final class MissionArrivalNotificationAdapter {
    private let claim: (String, String) throws -> AlarmRecoveryArrivalClaim
    private let center: any MissionArrivalNotificationCenter
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "Ringout", category: "MissionArrival")

    init(
        claim: @escaping (String, String) throws -> AlarmRecoveryArrivalClaim,
        center: any MissionArrivalNotificationCenter
    ) {
        self.claim = claim
        self.center = center
    }

    func notify(
        chainId: String,
        occurrenceId: String,
        fallbackDestinationName: String? = nil
    ) async -> MissionArrivalNotificationResult {
        let destinationName: String
        do {
            switch try claim(chainId, occurrenceId) {
            case .claimed(let name): destinationName = name
            case .alreadyHandled: return .alreadyHandled
            case .missingChain:
                guard let fallbackDestinationName else { return .missingChain }
                // Kotlin has already durably claimed this legacy pending record.
                destinationName = fallbackDestinationName
            }
        } catch {
            logger.error("Arrival notification claim could not be persisted.")
            return .claimStorageFailed
        }
        let status = await center.authorizationStatus()
        var authorized = status == .authorized || status == .provisional
        #if os(iOS)
        authorized = authorized || status == .ephemeral
        #endif
        guard authorized else {
            return .permissionSkipped
        }
        let content = UNMutableNotificationContent()
        content.title = "목적지에 도착했습니다."
        content.body = "\(destinationName) 미션을 완료했어요."
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: "ringout.destination-arrival.\(chainId)", content: content, trigger: nil
        )
        do {
            try await center.add(request)
            return .handled
        } catch {
            logger.error("Arrival notification OS request failed.")
            return .requestFailed
        }
    }
}

#if canImport(Shared)
extension MissionArrivalNotificationAdapter: @preconcurrency IosMissionArrivalNotifier {
    convenience init() {
        self.init(
            claim: { try AlarmRecoveryDriver.journal.claimArrivalNotification(chainId: $0, occurrenceId: $1) },
            center: SystemMissionArrivalNotificationCenter()
        )
    }

    func notify(request: IosMissionArrivalRequest, callback: @escaping (IosMissionArrivalResult) -> Void) {
        Task { @MainActor in
            let result = await notify(
                chainId: request.chainId, occurrenceId: request.occurrenceId,
                fallbackDestinationName: request.fallbackDestinationName
            )
            switch result {
            case .handled: callback(.handled)
            case .permissionSkipped: callback(.permissionSkipped)
            case .alreadyHandled: callback(.alreadyHandled)
            case .missingChain: callback(.missingChain)
            case .claimStorageFailed: callback(.claimStorageFailed)
            case .requestFailed: callback(.requestFailed)
            }
        }
    }
}
#endif
