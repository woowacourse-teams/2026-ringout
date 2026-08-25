package com.joon.ringout

import com.joon.ringout.domain.auth.AuthSessionState
import kotlin.test.Test
import kotlin.test.assertEquals

class AppRoutingPolicyTest {

    @Test
    fun `울리는 알람은 모든 화면 전환보다 우선한다`() {
        AppScreen.entries.forEach { requestedScreen ->
            AuthSessionState.entries.forEach { authSessionState ->
                assertEquals(
                    AppScreen.AlarmRinging,
                    resolveAppScreen(
                        requestedScreen = requestedScreen,
                        hasRingingAlarm = true,
                        hasActiveAlarmMission = false,
                        authSessionState = authSessionState,
                    ),
                )
            }
        }
    }

    @Test
    fun `알람이 울리지 않을 때 재인증은 모든 요청 화면보다 우선한다`() {
        AppScreen.entries.forEach { requestedScreen ->
            assertEquals(
                AppScreen.Login,
                resolveAppScreen(
                    requestedScreen = requestedScreen,
                    hasRingingAlarm = false,
                    hasActiveAlarmMission = true,
                    authSessionState = AuthSessionState.ReauthenticationRequired,
                ),
            )
        }
    }

    @Test
    fun `활성 미션이 없으면 알람 추적 화면 요청은 홈으로 이동한다`() {
        assertEquals(
            AppScreen.Home,
            resolveAppScreen(
                requestedScreen = AppScreen.ActiveAlarmTracking,
                hasRingingAlarm = false,
                hasActiveAlarmMission = false,
                authSessionState = AuthSessionState.Authenticated,
            ),
        )
    }

    @Test
    fun `활성 미션이 있으면 알람 추적 화면을 유지한다`() {
        assertEquals(
            AppScreen.ActiveAlarmTracking,
            resolveAppScreen(
                requestedScreen = AppScreen.ActiveAlarmTracking,
                hasRingingAlarm = false,
                hasActiveAlarmMission = true,
                authSessionState = AuthSessionState.Authenticated,
            ),
        )
    }

    @Test
    fun `알람이 울리는 동안 전역 다이얼로그를 표시하지 않는다`() {
        AuthSessionState.entries.forEach { authSessionState ->
            assertEquals(
                false,
                canShowAppDialog(
                    screen = AppScreen.AlarmRinging,
                    authSessionState = authSessionState,
                ),
            )
        }
    }

    @Test
    fun `일반 비인증 상태에서는 요청한 화면을 유지한다`() {
        assertEquals(
            AppScreen.Home,
            resolveAppScreen(
                requestedScreen = AppScreen.Home,
                hasRingingAlarm = false,
                hasActiveAlarmMission = false,
                authSessionState = AuthSessionState.Unauthenticated,
            ),
        )
    }

    @Test
    fun `재인증이 필요한 로그인 화면에서는 전역 다이얼로그를 표시하지 않는다`() {
        assertEquals(
            false,
            canShowAppDialog(
                screen = AppScreen.Login,
                authSessionState = AuthSessionState.ReauthenticationRequired,
            ),
        )
    }

    @Test
    fun `인증된 홈 화면에서는 전역 다이얼로그를 표시할 수 있다`() {
        assertEquals(
            true,
            canShowAppDialog(
                screen = AppScreen.Home,
                authSessionState = AuthSessionState.Authenticated,
            ),
        )
    }
}
