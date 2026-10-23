import Foundation
import Shared

@MainActor
final class RingoutAlarmMissionCommands: @preconcurrency IosAlarmMissionCommands {
    static let shared = RingoutAlarmMissionCommands()

    func dismissRingingAlarm(request: IosAlarmDismissRequest, callback: @escaping (IosAlarmDismissResult) -> Void) {
        Task {
            let outcome = await AlarmRecoveryDriver.dismiss(
                alarmId: request.sourceAlarmId,
                occurrenceToken: request.occurrenceId,
                retryAttempt: Int(request.retryAttempt),
                scheduleVersion: request.scheduleVersion,
                systemAlarmId: request.systemAlarmId,
                chainId: request.chainId
            )
            let recovery = outcome.state?.toRecoveryResult() ?? IosAlarmRecoveryAttemptsResult(
                attempts: [], terminals: [], code: .sdkError, message: outcome.message
            )
            let disposition: IosAlarmDismissDisposition
            switch outcome.disposition {
            case .handled: disposition = .handled
            case .fallback: disposition = .fallback
            case .preserved: disposition = .preserved
            case .failed: disposition = .failed
            }
            let code: IosAlarmOperationCode
            if outcome.disposition == .handled { code = .success }
            else if outcome.osStopCode == .notFound { code = .notFound }
            else if outcome.osStopCode == .invalidId { code = .invalidId }
            else { code = .sdkError }
            callback(IosAlarmDismissResult(
                osStopCode: outcome.osStopCode.toDto(), disposition: disposition,
                recovery: recovery, occurrenceId: outcome.occurrenceId, code: code, message: outcome.message
            ))
        }
    }

    func readRecoverySnapshot(callback: @escaping (IosAlarmRecoveryAttemptsResult) -> Void) {
        do {
            callback(try AlarmRecoveryDriver.journal.recoveryState().toRecoveryResult())
        } catch {
            callback(IosAlarmRecoveryAttemptsResult(
                attempts: [], terminals: [], code: .sdkError, message: error.localizedDescription
            ))
        }
    }
}

private extension AlarmRecoveryOSStopCode {
    func toDto() -> IosAlarmOperationCode {
        switch self {
        case .success: return .success
        case .notFound: return .notFound
        case .invalidId: return .invalidId
        case .failed: return .sdkError
        case .notAttempted: return .unknownError
        }
    }
}
