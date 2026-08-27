package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.joon.ringout.AppScreen
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.resolveAppScreen
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AppNavigationStateTest {
    @Test
    fun `홈에서 마이페이지와 닉네임으로 이동하고 역순으로 돌아온다`() {
        val state = AppNavigationState()

        state.navigate(AppRoute.MyPage)
        assertEquals(AppScreen.MyPage, state.requestedScreen)
        state.navigate(AppRoute.NicknameChange)
        assertEquals(
            listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.NicknameChange),
            state.backStack.toList(),
        )
        assertEquals(AppScreen.NicknameChange, state.requestedScreen)

        state.popBackStack()
        assertEquals(listOf(AppRoute.Home, AppRoute.MyPage), state.backStack.toList())
        state.popBackStack()
        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
        assertEquals(AppScreen.Home, state.requestedScreen)
    }

    @Test
    fun `같은 화면을 반복해서 요청해도 중복 경로를 추가하지 않는다`() {
        val state = AppNavigationState()

        repeat(2) { state.navigate(AppRoute.MyPage) }
        repeat(2) { state.navigate(AppRoute.NicknameChange) }

        assertEquals(
            listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.NicknameChange),
            state.backStack.toList(),
        )
    }

    @Test
    fun `이미 나간 화면의 뒤로 가기는 현재 화면을 제거하지 않는다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.NicknameChange)

        state.popBackStack(AppRoute.MyPage)
        assertEquals(AppScreen.NicknameChange, state.requestedScreen)
        repeat(2) { state.popBackStack(AppRoute.NicknameChange) }

        assertEquals(listOf(AppRoute.Home, AppRoute.MyPage), state.backStack.toList())
    }

    @Test
    fun `홈에서는 뒤로 가도 백스택이 비지 않는다`() {
        val state = AppNavigationState()

        state.popBackStack()

        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
    }

    @Test
    fun `기존 로그인 화면에 있는 동안에는 마이페이지 백스택을 유지한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.MyPage)

        state.navigate(AppScreen.Login)
        state.popBackStack(AppRoute.MyPage)

        assertEquals(AppScreen.Login, state.requestedScreen)
        assertEquals(listOf(AppRoute.Home, AppRoute.MyPage), state.backStack.toList())

        state.navigate(AppScreen.MyPage)
        assertEquals(AppScreen.MyPage, state.requestedScreen)
        assertEquals(listOf(AppRoute.Home, AppRoute.MyPage), state.backStack.toList())
    }

    @Test
    fun `로그인에서 마이페이지로 복귀하면 홈까지의 경로가 만들어진다`() {
        val state = AppNavigationState()
        state.navigate(AppScreen.Login)

        state.navigate(AppScreen.MyPage)

        assertEquals(listOf(AppRoute.Home, AppRoute.MyPage), state.backStack.toList())
        state.popBackStack()
        assertEquals(AppScreen.Home, state.requestedScreen)
    }

    @Test
    fun `기존 화면에서 홈으로 이동하면 이전 편집 경로를 제거한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.NicknameChange)
        state.navigate(AppScreen.Login)

        state.navigate(AppScreen.Home)

        assertEquals(listOf(AppRoute.Home), state.backStack.toList())
        assertEquals(AppScreen.Home, state.requestedScreen)
    }

    @Test
    fun `이전 Settings 경로는 마이페이지 경로로 통합한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.NicknameChange)

        state.navigate(AppScreen.Settings)

        assertEquals(listOf(AppRoute.Home, AppRoute.MyPage), state.backStack.toList())
        assertEquals(AppScreen.MyPage, state.requestedScreen)
    }

    @Test
    fun `알람과 재인증의 표시 우선순위는 닉네임 백스택을 변경하지 않는다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.NicknameChange)
        val expectedStack = state.backStack.toList()

        assertEquals(
            AppScreen.AlarmRinging,
            resolveAppScreen(
                requestedScreen = state.requestedScreen,
                hasRingingAlarm = true,
                hasActiveAlarmMission = false,
                authSessionState = AuthSessionState.ReauthenticationRequired,
            ),
        )
        assertEquals(
            AppScreen.Login,
            resolveAppScreen(
                requestedScreen = state.requestedScreen,
                hasRingingAlarm = false,
                hasActiveAlarmMission = false,
                authSessionState = AuthSessionState.ReauthenticationRequired,
            ),
        )
        assertEquals(expectedStack, state.backStack.toList())
        assertEquals(AppScreen.NicknameChange, state.requestedScreen)
    }

    @Test
    fun `직렬화한 백스택과 기존 화면을 복원한 뒤 마이페이지로 돌아갈 수 있다`() {
        val backStack = NavBackStack<AppRoute>(
            AppRoute.Home,
            AppRoute.MyPage,
            AppRoute.NicknameChange,
        )
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        val saved = Json.encodeToString(serializer, backStack)
        val restored = Json.decodeFromString(serializer, saved)
        val state = AppNavigationState(restored, mutableStateOf(AppScreen.Login.name))

        assertEquals(backStack.toList(), state.backStack.toList())
        assertEquals(AppScreen.Login, state.requestedScreen)
        state.navigate(AppScreen.MyPage)
        state.popBackStack()
        assertEquals(AppScreen.Home, state.requestedScreen)
    }
}
