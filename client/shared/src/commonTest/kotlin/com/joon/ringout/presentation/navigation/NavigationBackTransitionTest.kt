package com.joon.ringout.presentation.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationBackTransitionTest {
    @Test
    fun `제스처 미리보기는 백스택을 변경하지 않고 바로 이전 화면만 대상으로 한다`() {
        val state = AppNavigationState().apply {
            navigate(AppRoute.AddAlarm)
            navigate(AppRoute.AlarmSound)
        }

        assertTrue(state.isBackTransition(AppRoute.AlarmSound, AppRoute.AddAlarm))
        assertFalse(state.isBackTransition(AppRoute.AlarmSound, AppRoute.Home))
        assertFalse(state.isBackTransition(AppRoute.AddAlarm, AppRoute.AlarmSound))
        assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm, AppRoute.AlarmSound), state.backStack.toList())
    }

    @Test
    fun `뒤로가기로 제거한 화면은 퇴장 중에도 복귀 전환을 유지한다`() {
        val state = AppNavigationState().apply { navigate(AppRoute.AddAlarm) }

        state.popBackStack(AppRoute.AddAlarm)

        assertTrue(state.isBackTransition(AppRoute.AddAlarm, AppRoute.Home))
        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
        state.popBackStack(AppRoute.AddAlarm)
        assertEquals(listOf(AppRoute.Home), state.backStack.toList())

        state.navigate(AppRoute.MyPage)
        assertFalse(state.isBackTransition(AppRoute.AddAlarm, AppRoute.Home))
    }

    @Test
    fun `최상위 탭에서 홈으로 복귀할 때는 탭 선택과 뒤로가기 모두 전환을 적용하지 않는다`() {
        listOf(AppRoute.Social, AppRoute.Records, AppRoute.MyPage).forEach { tab ->
            val state = AppNavigationState().apply { navigate(tab) }

            assertFalse(state.isBackTransition(tab, AppRoute.Home))
            assertEquals(listOf(AppRoute.Home, tab), state.backStack.toList())

            state.popBackStack(tab)
            assertFalse(state.isBackTransition(tab, AppRoute.Home))
            assertEquals(listOf(AppRoute.Home), state.backStack.toList())

            state.navigate(tab)
            state.navigate(AppRoute.Home)
            assertFalse(state.isBackTransition(tab, AppRoute.Home))
        }
    }

    @Test
    fun `하위 화면에서 최상위 탭으로 돌아갈 때는 뒤로가기 전환을 유지한다`() {
        listOf(
            AppRoute.AddAlarm to AppRoute.Home,
            AppRoute.RoomDetail("1") to AppRoute.Social,
            AppRoute.NicknameChange to AppRoute.MyPage,
        ).forEach { (child, tab) ->
            val state = AppNavigationState().apply { navigate(child) }

            assertTrue(state.isBackTransition(child, tab))

            state.popBackStack(child)
            assertTrue(state.isBackTransition(child, tab))
            assertEquals(tab, state.requestedRoute)
            assertFalse(state.isBackTransition(tab, AppRoute.Home))
        }
    }

    @Test
    fun `인증 완료로 백스택을 정리할 때는 뒤로가기 전환을 적용하지 않는다`() {
        val state = AppNavigationState().apply {
            navigate(AppRoute.Login)
            navigate(AppRoute.TermsAgreement)
        }

        state.completeAuthenticationFlow()

        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
        assertFalse(state.isBackTransition(AppRoute.TermsAgreement, AppRoute.Home))
    }

    @Test
    fun `알람 실행 화면은 미리보기와 복귀 전환 대상에서 제외한다`() {
        val state = AppNavigationState()
        val tracking = AppRoute.ActiveAlarmTracking("mission-1")
        state.navigate(tracking)

        assertFalse(state.isBackTransition(tracking, AppRoute.Home))
        state.popBackStack(tracking)
        assertFalse(state.isBackTransition(tracking, AppRoute.Home))
        assertFalse(state.isBackTransition(AppRoute.AlarmRinging("alarm-1"), AppRoute.Home))
    }
}
