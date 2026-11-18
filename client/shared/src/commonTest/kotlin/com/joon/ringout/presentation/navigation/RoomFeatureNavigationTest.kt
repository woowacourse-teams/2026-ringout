package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import com.joon.ringout.domain.push.RoomActivityPushTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RoomFeatureNavigationTest {
    @Test
    fun `모임 비활성화 상태에서는 소셜 탭을 숨긴다`() {
        assertEquals(
            listOf(MainNavigationTab.Home, MainNavigationTab.Records, MainNavigationTab.MyPage),
            visibleMainNavigationTabs(),
        )
        assertEquals(MainNavigationTab.entries, visibleMainNavigationTabs(roomsEnabled = true))
    }

    @Test
    fun `모임의 모든 직접 진입 경로를 차단하면서 진행 중 미션을 유지한다`() {
        val state = AppNavigationState()
        val mission = AppRoute.ActiveAlarmTracking("active")
        state.navigate(mission)
        listOf(
            AppRoute.Social,
            AppRoute.RoomCreate,
            AppRoute.RoomDetail("41"),
            AppRoute.RoomHome("41"),
            AppRoute.RoomActivity("41", "2026-11-16"),
            AppRoute.RoomEdit("41"),
            AppRoute.RoomMemberManagement("41"),
        ).forEach { route ->
            state.navigate(route)
            assertEquals(listOf(AppRoute.Home, mission), state.backStack.toList())
        }
        val push = requireNotNull(RoomActivityPushTarget.parse(mapOf("roomId" to "41", "activityDate" to "2026-11-16")))
        assertFalse(state.openRoomActivityFromPush(push))
        assertFalse(state.navigateToRoomHomeFrom(AppRoute.RoomCreate, "41"))
        assertEquals(mission, state.requestedRoute)
    }

    @Test
    fun `복원된 모임 경로만 정리하고 개인 미션과 도착 화면을 보존한다`() {
        val mission = AppRoute.ActiveAlarmTracking("active")
        val state = AppNavigationState(NavBackStack(AppRoute.Home, AppRoute.Social, AppRoute.RoomHome("41"), mission))
        assertEquals(listOf(AppRoute.Home, mission), state.backStack.toList())
        val arrival = AppRoute.Arrival("completed")
        val arrivalState = AppNavigationState(NavBackStack(AppRoute.Home, AppRoute.RoomHome("41"), arrival))
        assertEquals(listOf(AppRoute.Home, arrival), arrivalState.backStack.toList())
    }
}
