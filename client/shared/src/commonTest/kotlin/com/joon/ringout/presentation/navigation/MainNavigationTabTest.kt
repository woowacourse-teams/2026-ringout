package com.joon.ringout.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainNavigationTabTest {
    @Test
    fun `탭은 홈 소셜 기록 마이페이지 순서로 구성된다`() {
        assertEquals(listOf("홈", "소셜", "기록", "마이페이지"), MainNavigationTab.entries.map { it.title })
    }

    @Test
    fun `탭을 반복 전환해도 이전 탭을 쌓지 않고 뒤로 가면 홈으로 돌아간다`() {
        val state = AppNavigationState(guestOnlyMode = true)
        repeat(3) {
            listOf(AppRoute.Social, AppRoute.Records, AppRoute.MyPage).forEach { route ->
                state.navigate(route)
                state.navigate(route)
                assertEquals(listOf(AppRoute.Home, route), state.backStack.toList())
                assertEquals(route, state.requestedRoute.mainNavigationTab()?.route)
            }
        }
        state.popBackStack()
        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
    }

    @Test
    fun `알람 편집과 미션 화면에서는 네비게이션 바를 표시하지 않는다`() {
        listOf(
            AppRoute.AddAlarm, AppRoute.EditAlarm("alarm"), AppRoute.Destination(1),
            AppRoute.AlarmSound, AppRoute.Onboarding, AppRoute.AlarmRinging("alarm"),
            AppRoute.ActiveAlarmTracking("mission"),
        ).forEach { assertNull(it.mainNavigationTab()) }
    }
}
