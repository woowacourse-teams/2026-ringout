package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.presentation.activemission.ActiveAlarmTrackingScreen
import com.joon.ringout.presentation.ringing.AlarmRingingScreen
import com.joon.ringout.presentation.ringing.AlarmRingingUiState

/** 알람 울림과 진행 중인 미션처럼 앱 실행 상태에서 제공되는 화면을 등록한다. */
internal fun EntryProviderScope<AppRoute>.alarmRuntimeGraph(
    navigationState: AppNavigationState,
    ringingAlarm: AlarmRingingUiState?,
    activeAlarmMission: ActiveAlarmMission?,
    activeAlarmMissionLocation: ActiveAlarmMissionLocation?,
    missionLocationState: MissionLocationState,
    onRingingAlarmDismiss: (String) -> Unit,
    onActiveAlarmMissionExpired: () -> Unit,
    onActiveAlarmMissionForceEnd: (occurrenceId: String) -> Unit,
    onActiveAlarmMissionForceEndHoldStarted: (occurrenceId: String) -> Unit,
    onActiveAlarmMissionForceEndHoldCancelled:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit,
    onActiveAlarmMissionForceEndHoldCompleted:
        (occurrenceId: String, holdDurationMillis: Long) -> Unit,
) {
    entry<AppRoute.AlarmRinging>(clazzContentKey = AppRoute::viewModelStoreKey) {
        ringingAlarm?.let { alarm ->
            AlarmRingingScreen(
                alarmTime = alarm.alarmTime,
                dateText = alarm.dateText,
                limitMinutes = alarm.limitMinutes,
                destinationName = alarm.destinationName,
                onDismissAndNavigateClick = {
                    dismissRingingAlarm(
                        navigationState = navigationState,
                        alarmId = alarm.id,
                        activeMissionOccurrenceId = activeAlarmMission?.occurrenceId,
                        onRingingAlarmDismiss = onRingingAlarmDismiss,
                    )
                },
            )
        }
    }

    entry<AppRoute.ActiveAlarmTracking>(clazzContentKey = AppRoute::viewModelStoreKey) {
        activeAlarmMission?.let { mission ->
            ActiveAlarmTrackingScreen(
                mission = mission,
                currentLocation = activeAlarmMissionLocation,
                locationState = missionLocationState,
                onBackClick = { navigationState.navigate(AppRoute.Home) },
                onForceEndClick = onActiveAlarmMissionForceEnd,
                onForceEndHoldStarted = onActiveAlarmMissionForceEndHoldStarted,
                onForceEndHoldCancelled = onActiveAlarmMissionForceEndHoldCancelled,
                onForceEndHoldCompleted = onActiveAlarmMissionForceEndHoldCompleted,
                onExpired = onActiveAlarmMissionExpired,
            )
        }
    }
}

/** 알람을 해제한 뒤 진행 중인 미션 또는 홈으로 이동하고 플랫폼 알람 상태를 정리한다. */
internal fun dismissRingingAlarm(
    navigationState: AppNavigationState,
    alarmId: String,
    activeMissionOccurrenceId: String?,
    onRingingAlarmDismiss: (String) -> Unit,
) {
    val destination = activeMissionOccurrenceId
        ?.let { occurrenceId -> AppRoute.ActiveAlarmTracking(occurrenceId) }
        ?: AppRoute.Home
    navigationState.navigate(destination)
    onRingingAlarmDismiss(alarmId)
}
