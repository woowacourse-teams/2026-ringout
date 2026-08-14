import AlarmKit
import AppIntents
import Foundation
import Shared
import SwiftUI

private struct RingoutAlarmMetadata: AlarmMetadata {
    let alarmId: String
}

@MainActor
final class AlarmKitAdapter: @MainActor IosAlarmScheduler {
    private typealias RingoutAlarmConfiguration =
        AlarmManager.AlarmConfiguration<RingoutAlarmMetadata>
    private weak var stateListener: IosAlarmStateListener?
    private var alarmUpdatesTask: Task<Void, Never>?

    init() {
        alarmUpdatesTask = Task { [weak self] in
            for await alarms in AlarmManager.shared.alarmUpdates {
                guard !Task.isCancelled else { return }
                self?.publishAlarmSnapshot(alarms)
            }
        }
    }

    deinit {
        alarmUpdatesTask?.cancel()
    }

    func authorizationState() -> IosAlarmAuthorizationState {
        mapAuthorizationState(AlarmManager.shared.authorizationState)
    }

    func normalizeAlarmId(_ id: String) -> String? {
        UUID(uuidString: id)?.uuidString
    }

    func requestAuthorization(callback: @escaping (IosAlarmAuthorizationResult) -> Void) {
        Task {
            do {
                let state = try await AlarmManager.shared.requestAuthorization()
                completeOnMain {
                    callback(
                        IosAlarmAuthorizationResult(
                            state: mapAuthorizationState(state),
                            code: .success,
                            message: nil
                        )
                    )
                }
            } catch {
                completeOnMain {
                    callback(
                        IosAlarmAuthorizationResult(
                            state: .notDetermined,
                            code: .sdkError,
                            message: error.localizedDescription
                        )
                    )
                }
            }
        }
    }

    func schedule(
        request: IosAlarmScheduleDto,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        guard AlarmManager.shared.authorizationState == .authorized else {
            callback(IosAlarmOperationResult(code: .denied, message: nil))
            return
        }

        guard let alarmId = UUID(uuidString: request.alarmId) else {
            callback(IosAlarmOperationResult(code: .invalidId, message: nil))
            return
        }

        do {
            let configuration = try makeConfiguration(request: request, alarmId: alarmId)
            Task {
                do {
                    _ = try await AlarmManager.shared.schedule(
                        id: alarmId,
                        configuration: configuration
                    )
                    completeOnMain {
                        callback(IosAlarmOperationResult(code: .success, message: nil))
                    }
                } catch {
                    completeOnMain {
                        callback(mapScheduleError(error))
                    }
                }
            }
        } catch let error as AlarmKitAdapterError {
            callback(IosAlarmOperationResult(code: error.resultCode, message: nil))
        } catch {
            callback(
                IosAlarmOperationResult(
                    code: .unknownError,
                    message: error.localizedDescription
                )
            )
        }
    }

    func scheduleRetry(
        request: IosAlarmRetryScheduleDto,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        guard AlarmManager.shared.authorizationState == .authorized else {
            callback(IosAlarmOperationResult(code: .denied, message: nil))
            return
        }
        guard UUID(uuidString: request.sourceAlarmId) != nil,
              let alarmKitId = UUID(uuidString: request.alarmKitId) else {
            callback(IosAlarmOperationResult(code: .invalidId, message: nil))
            return
        }

        let presentation = AlarmPresentation(alert: makeAlert(title: request.title))
        let attributes = AlarmAttributes(
            presentation: presentation,
            metadata: RingoutAlarmMetadata(alarmId: request.sourceAlarmId),
            tintColor: .orange
        )
        let stopIntent = StopAlarmIntent(
            alarmId: request.sourceAlarmId,
            occurrenceId: request.occurrenceId,
            retryAttempt: Int(request.retryAttempt),
            systemAlarmId: request.alarmKitId
        )
        let openIntent = OpenRingoutIntent(
            alarmId: request.sourceAlarmId,
            occurrenceId: request.occurrenceId,
            retryAttempt: Int(request.retryAttempt),
            systemAlarmId: request.alarmKitId
        )
        let fireDate = Date().addingTimeInterval(max(0, request.delaySeconds))
        let configuration = AlarmManager.AlarmConfiguration.alarm(
            schedule: .fixed(fireDate),
            attributes: attributes,
            stopIntent: stopIntent,
            secondaryIntent: openIntent,
            sound: .default
        )
        Task {
            do {
                _ = try await AlarmManager.shared.schedule(
                    id: alarmKitId,
                    configuration: configuration
                )
                completeOnMain {
                    callback(IosAlarmOperationResult(code: .success, message: nil))
                }
            } catch {
                completeOnMain {
                    callback(mapScheduleError(error))
                }
            }
        }
    }

    func cancel(
        alarmId rawAlarmId: String,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        guard let alarmId = UUID(uuidString: rawAlarmId) else {
            callback(IosAlarmOperationResult(code: .invalidId, message: nil))
            return
        }

        do {
            guard try AlarmManager.shared.alarms.contains(where: { $0.id == alarmId }) else {
                callback(IosAlarmOperationResult(code: .notFound, message: nil))
                return
            }
            try AlarmManager.shared.cancel(id: alarmId)
            callback(IosAlarmOperationResult(code: .success, message: nil))
        } catch {
            callback(
                IosAlarmOperationResult(
                    code: .sdkError,
                    message: error.localizedDescription
                )
            )
        }
    }

    func stop(
        alarmId rawAlarmId: String,
        callback: @escaping (IosAlarmOperationResult) -> Void
    ) {
        guard let alarmId = UUID(uuidString: rawAlarmId) else {
            callback(IosAlarmOperationResult(code: .invalidId, message: nil))
            return
        }

        Task {
            do {
                guard try AlarmManager.shared.alarms.contains(where: { $0.id == alarmId }) else {
                    callback(IosAlarmOperationResult(code: .notFound, message: nil))
                    return
                }
                try AlarmManager.shared.stop(id: alarmId)
                callback(IosAlarmOperationResult(code: .success, message: nil))
            } catch {
                callback(
                    IosAlarmOperationResult(
                        code: .sdkError,
                        message: error.localizedDescription
                    )
                )
            }
        }
    }

    func scheduledAlarms(callback: @escaping (IosScheduledAlarmsResult) -> Void) {
        do {
            callback(
                IosScheduledAlarmsResult(
                    alarms: try AlarmManager.shared.alarms.map(makeScheduledAlarmDto),
                    code: .success,
                    message: nil
                )
            )
        } catch {
            callback(
                IosScheduledAlarmsResult(
                    alarms: [],
                    code: .sdkError,
                    message: error.localizedDescription
                )
            )
        }
    }

    func setStateListener(listener: IosAlarmStateListener?) {
        stateListener = listener
        do {
            publishAlarmSnapshot(try AlarmManager.shared.alarms)
        } catch {
            listener?.onError(message: error.localizedDescription)
        }
    }

    private static var compileSpikeConfigurationType: Any.Type {
        RingoutAlarmConfiguration.self
    }

    private func makeConfiguration(
        request: IosAlarmScheduleDto,
        alarmId: UUID
    ) throws -> RingoutAlarmConfiguration {
        let alarmTime = try makeAlarmTime(
            hour: Int(request.hour),
            minute: Int(request.minute)
        )
        let repeats = try makeRecurrence(
            repeats: request.repeats,
            isoWeekdays: request.isoWeekdays.map { Int($0.intValue) }
        )
        let schedule = Alarm.Schedule.relative(
            Alarm.Schedule.Relative(time: alarmTime, repeats: repeats)
        )
        let presentation = AlarmPresentation(alert: makeAlert(title: request.title))
        let attributes = AlarmAttributes(
            presentation: presentation,
            metadata: RingoutAlarmMetadata(alarmId: alarmId.uuidString),
            tintColor: .orange
        )

        return AlarmManager.AlarmConfiguration.alarm(
            schedule: schedule,
            attributes: attributes,
            stopIntent: StopAlarmIntent(
                alarmId: alarmId.uuidString,
                systemAlarmId: alarmId.uuidString
            ),
            secondaryIntent: OpenRingoutIntent(
                alarmId: alarmId.uuidString,
                systemAlarmId: alarmId.uuidString
            ),
            sound: .default
        )
    }

    private func makeAlarmTime(hour: Int, minute: Int) throws -> Alarm.Schedule.Relative.Time {
        guard (0...23).contains(hour), (0...59).contains(minute) else {
            throw AlarmKitAdapterError.invalidSchedule
        }
        return Alarm.Schedule.Relative.Time(hour: hour, minute: minute)
    }

    private func makeRecurrence(
        repeats: Bool,
        isoWeekdays: [Int]
    ) throws -> Alarm.Schedule.Relative.Recurrence {
        guard repeats else { return .never }
        let weekdays = try isoWeekdays.map { isoWeekday in
            guard let weekday = localeWeekday(isoWeekday: isoWeekday) else {
                throw AlarmKitAdapterError.invalidSchedule
            }
            return weekday
        }
        return .weekly(weekdays)
    }

    private func makeAlert(title: String) -> AlarmPresentation.Alert {
        let secondaryButton = AlarmButton(
            text: "미션 시작",
            textColor: .white,
            systemImageName: "figure.walk"
        )

        if #available(iOS 26.1, *) {
            return AlarmPresentation.Alert(
                title: LocalizedStringResource(String.LocalizationValue(title)),
                secondaryButton: secondaryButton,
                secondaryButtonBehavior: .custom
            )
        } else {
            return AlarmPresentation.Alert(
                title: LocalizedStringResource(String.LocalizationValue(title)),
                stopButton: AlarmButton(
                    text: "중지",
                    textColor: .white,
                    systemImageName: "stop.fill"
                ),
                secondaryButton: secondaryButton,
                secondaryButtonBehavior: .custom
            )
        }
    }

    private func localeWeekday(isoWeekday: Int) -> Locale.Weekday? {
        switch isoWeekday {
        case 1: return .monday
        case 2: return .tuesday
        case 3: return .wednesday
        case 4: return .thursday
        case 5: return .friday
        case 6: return .saturday
        case 7: return .sunday
        default: return nil
        }
    }

    private func mapAuthorizationState(
        _ state: AlarmManager.AuthorizationState
    ) -> IosAlarmAuthorizationState {
        switch state {
        case .notDetermined:
            return .notDetermined
        case .denied:
            return .denied
        case .authorized:
            return .authorized
        @unknown default:
            return .notDetermined
        }
    }

    private func mapAlarmState(_ state: Alarm.State) -> IosScheduledAlarmState {
        switch state {
        case .scheduled:
            return .scheduled
        case .countdown:
            return .countdown
        case .paused:
            return .paused
        case .alerting:
            return .alerting
        @unknown default:
            return .unknown
        }
    }

    private func makeScheduledAlarmDto(_ alarm: Alarm) -> IosScheduledAlarmDto {
        IosScheduledAlarmDto(
            alarmId: alarm.id.uuidString,
            state: mapAlarmState(alarm.state)
        )
    }

    private func publishAlarmSnapshot(_ alarms: [Alarm]) {
        stateListener?.onAlarmsChanged(
            alarms: alarms.map(makeScheduledAlarmDto)
        )
    }

    private func mapScheduleError(_ error: Error) -> IosAlarmOperationResult {
        IosAlarmOperationResult(
            code: .sdkError,
            message: error.localizedDescription
        )
    }

    @MainActor
    private func completeOnMain(_ completion: () -> Void) {
        completion()
    }
}

private enum AlarmKitAdapterError: Error {
    case invalidSchedule

    var resultCode: IosAlarmOperationCode {
        switch self {
        case .invalidSchedule:
            return .invalidId
        }
    }
}
