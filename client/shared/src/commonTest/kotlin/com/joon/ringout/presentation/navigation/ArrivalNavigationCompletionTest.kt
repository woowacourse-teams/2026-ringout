package com.joon.ringout.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ArrivalNavigationCompletionTest {
    @Test
    fun `확인한 결과가 관찰에서도 사라지면 도착 스택을 홈으로 정리한다`() {
        val navigation = AppNavigationState().apply { navigate(AppRoute.Arrival("A")) }
        completeArrivalNavigation(navigation, "A", null, isArrivalStateLoaded = true, hasActiveRuntime = false)
        assertEquals(listOf(AppRoute.Home), navigation.backStack.toList())
    }

    @Test
    fun `확인 성공 직후 아직 갱신되지 않은 결과는 관찰 완료까지 화면을 유지한다`() {
        val navigation = AppNavigationState().apply { navigate(AppRoute.Arrival("A")) }
        completeArrivalNavigation(navigation, "A", "A", isArrivalStateLoaded = true, hasActiveRuntime = false)
        assertEquals(AppRoute.Arrival("A"), navigation.requestedRoute)
        completeArrivalNavigation(navigation, "A", null, isArrivalStateLoaded = false, hasActiveRuntime = false)
        assertEquals(AppRoute.Arrival("A"), navigation.requestedRoute)
    }

    @Test
    fun `이전 결과의 늦은 확인 완료는 새로운 도착 결과와 알람을 덮어쓰지 않는다`() {
        val navigation = AppNavigationState().apply { navigate(AppRoute.Arrival("A")) }
        completeArrivalNavigation(navigation, "A", "B", isArrivalStateLoaded = true, hasActiveRuntime = false)
        assertEquals(AppRoute.Arrival("A"), navigation.requestedRoute)
        navigation.navigate(AppRoute.Arrival("B"))
        completeArrivalNavigation(navigation, "A", null, isArrivalStateLoaded = true, hasActiveRuntime = false)
        assertEquals(AppRoute.Arrival("B"), navigation.requestedRoute)
        completeArrivalNavigation(navigation, "B", null, isArrivalStateLoaded = true, hasActiveRuntime = true)
        assertEquals(AppRoute.Arrival("B"), navigation.requestedRoute)
        navigation.navigate(AppRoute.ActiveAlarmTracking("new-mission"))
        completeArrivalNavigation(navigation, "B", null, isArrivalStateLoaded = true, hasActiveRuntime = false)
        assertEquals(AppRoute.ActiveAlarmTracking("new-mission"), navigation.requestedRoute)
    }
}
