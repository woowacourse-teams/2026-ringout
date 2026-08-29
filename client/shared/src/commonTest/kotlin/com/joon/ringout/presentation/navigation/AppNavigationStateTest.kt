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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun `식별자 경로는 전체 값 기준 singleTop으로 기존 항목을 유지한다`() {
        val state = AppNavigationState()
        val firstEditor = AppRoute.EditAlarm("alarm-1")

        state.navigate(firstEditor)
        state.navigate(firstEditor.copy())

        assertSame(firstEditor, state.backStack.last())
        assertEquals(listOf(AppRoute.Home, firstEditor), state.backStack.toList())

        val nextEditor = AppRoute.EditAlarm("alarm-2")
        state.navigate(nextEditor)

        assertSame(nextEditor, state.backStack.last())
        assertEquals(listOf(AppRoute.Home, nextEditor), state.backStack.toList())

        val firstDestination = AppRoute.Destination(1L)
        state.navigate(firstDestination)
        state.navigate(firstDestination.copy())

        assertSame(firstDestination, state.backStack.last())
        assertEquals(
            listOf(AppRoute.Home, nextEditor, firstDestination),
            state.backStack.toList(),
        )

        val nextDestination = AppRoute.Destination(2L)
        state.navigate(nextDestination)

        assertSame(nextDestination, state.backStack.last())
        assertEquals(
            listOf(AppRoute.Home, nextEditor, nextDestination),
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
    fun `로그인은 마이페이지 다음에 쌓이고 이전 화면의 뒤로 가기를 무시한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.MyPage)

        state.navigate(AppScreen.Login)
        state.popBackStack(AppRoute.MyPage)

        assertEquals(AppScreen.Login, state.requestedScreen)
        assertEquals(
            listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login),
            state.backStack.toList(),
        )

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
        state.navigate(AppScreen.ActiveAlarmTracking)

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
            AppRoute.Login,
            AppRoute.TermsAgreement,
        )
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        val saved = Json.encodeToString(serializer, backStack)
        val restored = Json.decodeFromString(serializer, saved)
        val state = AppNavigationState(restored, mutableStateOf(AppScreen.ActiveAlarmTracking.name))

        assertEquals(backStack.toList(), state.backStack.toList())
        assertEquals(AppScreen.ActiveAlarmTracking, state.requestedScreen)
        state.navigate(AppScreen.MyPage)
        state.popBackStack()
        assertEquals(AppScreen.Home, state.requestedScreen)
    }

    @Test
    fun `이전 버전에 저장한 로그인과 약관 화면은 타입 안전 경로로 복원한다`() {
        for (screen in listOf(AppScreen.Login, AppScreen.TermsAgreement)) {
            val state = AppNavigationState(
                NavBackStack(AppRoute.Home, AppRoute.MyPage, AppRoute.NicknameChange),
                mutableStateOf(screen.name),
            )
            val expectedStack = when (screen) {
                AppScreen.Login -> listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login)
                else ->
                    listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login, AppRoute.TermsAgreement)
            }

            assertEquals(expectedStack, state.backStack.toList())
            assertEquals(screen, state.requestedScreen)
            assertTrue(state.isCurrentRoute(expectedStack.last()))
            state.popBackStack()
            assertEquals(expectedStack.dropLast(1), state.backStack.toList())
        }
    }

    @Test
    fun `로그인에서 약관으로 이동하고 역순으로 마이페이지까지 돌아온다`() {
        val state = AppNavigationState()

        state.navigate(AppScreen.Login)
        state.navigate(AppScreen.TermsAgreement)

        assertEquals(
            listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login, AppRoute.TermsAgreement),
            state.backStack.toList(),
        )
        assertEquals(AppScreen.TermsAgreement, state.requestedScreen)
        state.popBackStack(AppRoute.TermsAgreement)
        assertEquals(AppScreen.Login, state.requestedScreen)
        state.popBackStack(AppRoute.Login)
        assertEquals(listOf(AppRoute.Home, AppRoute.MyPage), state.backStack.toList())
    }

    @Test
    fun `반복 로그인과 약관 진입은 인증 경로를 중복하지 않는다`() {
        val state = AppNavigationState()

        repeat(2) { state.navigate(AppRoute.Login) }
        repeat(2) { state.navigate(AppRoute.TermsAgreement) }

        assertEquals(
            listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login, AppRoute.TermsAgreement),
            state.backStack.toList(),
        )
    }

    @Test
    fun `로그아웃이나 재인증의 로그인 진입은 이전 편집과 약관 경로를 제거한다`() {
        val state = AppNavigationState()

        for (previousRoute in listOf(AppRoute.NicknameChange, AppRoute.TermsAgreement)) {
            state.navigate(previousRoute)

            state.navigate(AppScreen.Login)

            assertEquals(
                listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login),
                state.backStack.toList(),
            )
            assertTrue(state.isCurrentRoute(AppRoute.Login))
        }
    }

    @Test
    fun `기존 알람 화면에 진입해도 약관 백스택은 유지하고 뒤의 콜백은 무시한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.TermsAgreement)
        val expectedStack = state.backStack.toList()

        state.navigate(AppScreen.ActiveAlarmTracking)
        state.popBackStack(AppRoute.TermsAgreement)

        assertEquals(AppScreen.ActiveAlarmTracking, state.requestedScreen)
        assertEquals(expectedStack, state.backStack.toList())
        assertFalse(state.isCurrentRoute(AppRoute.TermsAgreement))
        assertNull(state.routesForScreen(AppScreen.ActiveAlarmTracking))

        state.navigate(AppRoute.TermsAgreement)

        assertEquals(expectedStack, state.backStack.toList())
        assertTrue(state.isCurrentRoute(AppRoute.TermsAgreement))
    }

    @Test
    fun `표시할 인증 화면의 경로만 투영해도 실제 백스택은 바꾸지 않는다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.TermsAgreement)
        val expectedStack = state.backStack.toList()

        assertEquals(
            listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Login),
            state.routesForScreen(AppScreen.Login)?.toList(),
        )
        assertEquals(
            listOf(AppRoute.Home, AppRoute.MyPage),
            state.routesForScreen(AppScreen.MyPage)?.toList(),
        )
        assertEquals(expectedStack, state.routesForScreen(AppScreen.TermsAgreement)?.toList())
        assertNull(state.routesForScreen(AppScreen.AlarmRinging))
        assertEquals(expectedStack, state.backStack.toList())
        assertTrue(state.isCurrentRoute(AppRoute.TermsAgreement))
        assertFalse(state.isCurrentRoute(AppRoute.Login))
    }

    @Test
    fun `재인증 우선 화면은 아직 스택에 없어도 로그인으로 투영한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.NicknameChange)
        val expectedStack = state.backStack.toList()

        val screen = resolveAppScreen(
            requestedScreen = state.requestedScreen,
            hasRingingAlarm = false,
            hasActiveAlarmMission = false,
            authSessionState = AuthSessionState.ReauthenticationRequired,
        )

        assertEquals(listOf(AppRoute.Login), state.routesForScreen(screen)?.toList())
        assertFalse(state.isCurrentRoute(AppRoute.Login))
        assertEquals(expectedStack, state.backStack.toList())
    }

    @Test
    fun `목적지와 알람음에서 뒤로 가면 같은 생성 또는 수정 화면으로 돌아온다`() {
        for (parent in listOf(AppRoute.AddAlarm, AppRoute.EditAlarm("alarm-1"))) {
            val state = AppNavigationState()
            state.navigate(parent)

            for (picker in listOf(AppRoute.Destination(7L), AppRoute.AlarmSound)) {
                state.navigate(picker)

                assertEquals(listOf(AppRoute.Home, parent, picker), state.backStack.toList())
                assertEquals(parent, state.editorRoute)
                state.popBackStack(picker)
                assertEquals(listOf(AppRoute.Home, parent), state.backStack.toList())
            }

            state.popBackStack(parent)
            assertEquals(listOf(AppRoute.Home), state.backStack.toList())
            assertNull(state.editorRoute)
        }
    }

    @Test
    fun `선택 화면을 반복하거나 바꿔도 편집 부모 아래에는 현재 자식만 남긴다`() {
        val state = AppNavigationState()
        val parent = AppRoute.EditAlarm("alarm-1")
        state.navigate(parent)

        repeat(2) { state.navigate(AppRoute.Destination(1L)) }
        repeat(2) { state.navigate(AppRoute.AlarmSound) }
        state.navigate(AppRoute.Destination(2L))

        assertEquals(
            listOf(AppRoute.Home, parent, AppRoute.Destination(2L)),
            state.backStack.toList(),
        )
        state.navigate(AppRoute.EditAlarm("alarm-2"))
        assertEquals(listOf(AppRoute.Home, AppRoute.EditAlarm("alarm-2")), state.backStack.toList())
        state.navigate(AppRoute.AddAlarm)
        assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), state.backStack.toList())
    }

    @Test
    fun `편집 부모가 없는 목적지와 알람음 이동은 기존 스택을 바꾸지 않고 거절한다`() {
        val state = AppNavigationState()
        state.navigate(AppRoute.MyPage)
        val expectedStack = state.backStack.toList()

        for (picker in listOf(AppRoute.Destination(1L), AppRoute.AlarmSound)) {
            assertFailsWith<IllegalArgumentException> { state.navigate(picker) }
        }

        assertEquals(expectedStack, state.backStack.toList())
    }

    @Test
    fun `기존 화면 adapter는 식별자 없는 수정과 목적지 요청을 거절한다`() {
        val state = AppNavigationState()
        state.navigate(AppScreen.AddAlarm)
        state.navigate(AppScreen.AlarmSound)
        val expectedStack = listOf(AppRoute.Home, AppRoute.AddAlarm, AppRoute.AlarmSound)

        for (screen in listOf(AppScreen.EditAlarm, AppScreen.Destination)) {
            assertFailsWith<IllegalArgumentException> { state.navigate(screen) }
        }

        assertEquals(expectedStack, state.backStack.toList())
        assertEquals(AppScreen.AlarmSound, state.requestedScreen)
    }

    @Test
    fun `편집 화면을 투영할 때 부모 식별자를 보존하고 실제 자식은 제거하지 않는다`() {
        val state = AppNavigationState()
        val parent = AppRoute.EditAlarm("alarm-1")
        state.navigate(parent)
        state.navigate(AppRoute.Destination(9L))
        val expectedStack = state.backStack.toList()

        assertEquals(
            listOf(AppRoute.Home, parent),
            state.routesForScreen(AppScreen.EditAlarm)?.toList(),
        )
        assertEquals(expectedStack, state.routesForScreen(AppScreen.Destination)?.toList())
        assertNull(state.routesForScreen(AppScreen.AlarmRinging))
        assertEquals(expectedStack, state.backStack.toList())

        state.navigate(AppRoute.AlarmSound)
        assertEquals(
            listOf(AppRoute.Home, parent, AppRoute.AlarmSound),
            state.routesForScreen(AppScreen.AlarmSound)?.toList(),
        )
    }

    @Test
    fun `이전 문자열 편집 경로는 초안을 복원할 수 없어 홈으로 돌아간다`() {
        for (
            screen in listOf(
                AppScreen.AddAlarm,
                AppScreen.EditAlarm,
                AppScreen.Destination,
                AppScreen.AlarmSound,
            )
        ) {
            val state = AppNavigationState(
                NavBackStack(AppRoute.Home, AppRoute.MyPage),
                mutableStateOf(screen.name),
            )

            assertEquals(listOf(AppRoute.Home), state.backStack.toList())
            assertEquals(AppScreen.Home, state.requestedScreen)
            assertTrue(state.isCurrentRoute(AppRoute.Home))
            assertNull(state.editorRoute)
        }
    }
}
