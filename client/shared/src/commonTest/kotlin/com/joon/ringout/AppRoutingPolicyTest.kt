package com.joon.ringout

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.common.AppMessageSource
import com.joon.ringout.presentation.common.resolveAppMessage
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

    @Test
    fun `홈 화면에서는 홈 오류를 표시한다`() {
        val message = resolveAppMessage(
            screen = AppScreen.Home,
            authSessionState = AuthSessionState.Authenticated,
            homeErrorMessage = "알람을 삭제하지 못했어요.",
            alarmSetupErrorMessage = "알람을 저장하지 못했어요.",
            destinationErrorMessage = "목적지를 저장하지 못했어요.",
        )

        assertEquals(AppMessageSource.Home, message?.source)
        assertEquals("알람을 삭제하지 못했어요.", message?.state?.message)
    }

    @Test
    fun `목적지 화면에서는 알람 설정 오류를 목적지 오류보다 먼저 표시한다`() {
        val message = resolveAppMessage(
            screen = AppScreen.Destination,
            authSessionState = AuthSessionState.Authenticated,
            homeErrorMessage = null,
            alarmSetupErrorMessage = "알람을 저장하지 못했어요.",
            destinationErrorMessage = "목적지를 저장하지 못했어요.",
        )

        assertEquals(AppMessageSource.AlarmSetup, message?.source)
        assertEquals("알람을 저장하지 못했어요.", message?.state?.message)
    }

    @Test
    fun `알람 설정 오류가 없으면 목적지 화면에서 목적지 오류를 표시한다`() {
        val message = resolveAppMessage(
            screen = AppScreen.Destination,
            authSessionState = AuthSessionState.Authenticated,
            homeErrorMessage = null,
            alarmSetupErrorMessage = null,
            destinationErrorMessage = "목적지를 저장하지 못했어요.",
        )

        assertEquals(AppMessageSource.Destination, message?.source)
        assertEquals("목적지를 처리할 수 없습니다", message?.state?.title)
    }

    @Test
    fun `현재 화면과 관계없는 기능 오류는 표시하지 않는다`() {
        assertEquals(
            null,
            resolveAppMessage(
                screen = AppScreen.MyPage,
                authSessionState = AuthSessionState.Authenticated,
                homeErrorMessage = "홈 오류",
                alarmSetupErrorMessage = "알람 설정 오류",
                destinationErrorMessage = "목적지 오류",
            ),
        )
    }
}
