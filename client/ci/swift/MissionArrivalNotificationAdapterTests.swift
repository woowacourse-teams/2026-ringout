import Foundation
import UserNotifications

@MainActor
private final class FakeArrivalCenter: MissionArrivalNotificationCenter {
    var status: UNAuthorizationStatus = .authorized
    var requests: [UNNotificationRequest] = []
    var settingsReads = 0
    var failRequest = false

    func authorizationStatus() async -> UNAuthorizationStatus {
        settingsReads += 1
        return status
    }

    func add(_ request: UNNotificationRequest) async throws {
        requests.append(request)
        if failRequest { throw CocoaError(.fileWriteUnknown) }
    }
}

@main
struct MissionArrivalNotificationAdapterTests {
    @MainActor
    static func main() async throws {
        let center = FakeArrivalCenter()
        var claimed = false
        let adapter = MissionArrivalNotificationAdapter(claim: { _, _ in
            if claimed { return .alreadyHandled }
            claimed = true
            return .claimed(destinationName: "회사")
        }, center: center)
        let first = await adapter.notify(chainId: "chain", occurrenceId: "occurrence")
        assert(first == .handled)
        let request = center.requests.first!
        assert(request.identifier == "ringout.destination-arrival.chain")
        assert(request.content.title == "목적지에 도착했습니다.")
        assert(request.content.body == "회사 미션을 완료했어요.")
        assert(request.content.sound != nil && request.trigger == nil && request.content.userInfo.isEmpty)
        let duplicate = await adapter.notify(chainId: "chain", occurrenceId: "occurrence")
        assert(duplicate == .alreadyHandled && center.requests.count == 1)

        for status in [UNAuthorizationStatus.denied, .notDetermined] {
            center.status = status
            let skipped = MissionArrivalNotificationAdapter(claim: { _, _ in .claimed(destinationName: "집") }, center: center)
            let result = await skipped.notify(chainId: "denied", occurrenceId: "occurrence")
            assert(result == .permissionSkipped && center.requests.count == 1)
        }
        center.status = .authorized
        center.failRequest = true
        claimed = false
        let failed = await adapter.notify(chainId: "error", occurrenceId: "occurrence")
        assert(failed == .requestFailed && claimed)
        let afterFailure = await adapter.notify(chainId: "error", occurrenceId: "occurrence")
        assert(afterFailure == .alreadyHandled && center.requests.count == 2)
        let storageFailure = MissionArrivalNotificationAdapter(claim: { _, _ in throw CocoaError(.fileWriteUnknown) }, center: center)
        let settingsBefore = center.settingsReads
        let storageResult = await storageFailure.notify(chainId: "storage", occurrenceId: "occurrence")
        assert(storageResult == .claimStorageFailed && center.settingsReads == settingsBefore)
        center.failRequest = false
        let missing = MissionArrivalNotificationAdapter(claim: { _, _ in .missingChain }, center: center)
        let missingResult = await missing.notify(chainId: "legacy", occurrenceId: "occurrence")
        assert(missingResult == .missingChain)
        let fallback = await missing.notify(chainId: "legacy", occurrenceId: "occurrence", fallbackDestinationName: "옛 목적지")
        assert(fallback == .handled && center.requests.last?.content.body == "옛 목적지 미션을 완료했어요.")
        let tombstone = MissionArrivalNotificationAdapter(claim: { _, _ in .alreadyHandled }, center: center)
        let tombstoneResult = await tombstone.notify(chainId: "compacted", occurrenceId: "occurrence", fallbackDestinationName: "집")
        assert(tombstoneResult == .alreadyHandled && center.requests.count == 3)
        print("8 mission arrival notification adapter scenarios passed")
    }
}
