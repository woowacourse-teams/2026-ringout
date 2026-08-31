package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.entryProvider
import com.joon.ringout.alarm.DefaultMissionLocationState
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmRuntimeGraphTest {
    @Test
    fun `알람 울림과 활성 미션 경로를 식별자별 content key로 등록한다`() {
        val provider = entryProvider<AppRoute> {
            alarmRuntimeGraph(
                navigationState = AppNavigationState(),
                ringingAlarm = null,
                activeAlarmMission = null,
                activeAlarmMissionLocation = null,
                missionLocationState = DefaultMissionLocationState,
                onRingingAlarmDismiss = {},
                onActiveAlarmMissionExpired = {},
                onActiveAlarmMissionForceEnd = {},
                onActiveAlarmMissionForceEndHoldStarted = {},
                onActiveAlarmMissionForceEndHoldCancelled = { _, _ -> },
                onActiveAlarmMissionForceEndHoldCompleted = { _, _ -> },
            )
        }
        val ringingRoute = AppRoute.AlarmRinging("alarm-1")
        val activeMissionRoute = AppRoute.ActiveAlarmTracking("occurrence-1")

        assertEquals(ringingRoute.viewModelStoreKey(), provider(ringingRoute).contentKey)
        assertEquals(activeMissionRoute.viewModelStoreKey(), provider(activeMissionRoute).contentKey)
    }

    @Test
    fun `알람 해제 시 활성 미션으로 먼저 이동한 뒤 플랫폼 알람을 정리한다`() {
        val navigationState = AppNavigationState()
        var routeWhenDismissed: AppRoute? = null
        var dismissedAlarmId: String? = null

        dismissRingingAlarm(
            navigationState = navigationState,
            alarmId = "alarm-1",
            activeMissionOccurrenceId = "occurrence-1",
            onRingingAlarmDismiss = { alarmId ->
                routeWhenDismissed = navigationState.requestedRoute
                dismissedAlarmId = alarmId
            },
        )

        assertEquals(AppRoute.ActiveAlarmTracking("occurrence-1"), routeWhenDismissed)
        assertEquals("alarm-1", dismissedAlarmId)
    }

    @Test
    fun `활성 미션이 없으면 홈으로 먼저 이동한 뒤 플랫폼 알람을 정리한다`() {
        val navigationState = AppNavigationState().apply { navigate(AppRoute.MyPage) }
        var routeWhenDismissed: AppRoute? = null

        dismissRingingAlarm(
            navigationState = navigationState,
            alarmId = "alarm-1",
            activeMissionOccurrenceId = null,
            onRingingAlarmDismiss = {
                routeWhenDismissed = navigationState.requestedRoute
            },
        )

        assertEquals(AppRoute.Home, routeWhenDismissed)
        assertEquals(listOf(AppRoute.Home), navigationState.backStack.toList())
    }
}
