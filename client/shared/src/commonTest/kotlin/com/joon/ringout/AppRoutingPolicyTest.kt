package com.joon.ringout

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.common.AppMessageSource
import com.joon.ringout.presentation.common.resolveAppMessage
import com.joon.ringout.presentation.navigation.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class AppRoutingPolicyTest {

    @Test
    fun `울리는 알람은 모든 화면 전환보다 우선한다`() {
        RequestedRoutes.forEach { requestedRoute ->
            AuthSessionState.entries.forEach { authSessionState ->
                listOf(false, true).forEach { hasActiveAlarmMission ->
                    assertEquals(
                        AppRoute.AlarmRinging("ringing-alarm"),
                        resolveAppScreen(
                            requestedRoute = requestedRoute,
                            ringingAlarmId = "ringing-alarm",
                            hasActiveAlarmMission = hasActiveAlarmMission,
                            authSessionState = authSessionState,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `알람이 울리지 않을 때 재인증은 모든 요청 화면보다 우선한다`() {
        RequestedRoutes.forEach { requestedRoute ->
            assertEquals(
                AppRoute.Login,
                resolveAppScreen(
                    requestedRoute = requestedRoute,
                    ringingAlarmId = null,
                    hasActiveAlarmMission = true,
                    authSessionState = AuthSessionState.ReauthenticationRequired,
                ),
            )
        }
    }

    @Test
    fun `활성 미션이 없으면 알람 추적 화면 요청은 홈으로 이동한다`() {
        assertEquals(
            AppRoute.Home,
            resolveAppScreen(
                requestedRoute = ActiveMissionRoute,
                ringingAlarmId = null,
                hasActiveAlarmMission = false,
                authSessionState = AuthSessionState.Authenticated,
            ),
        )
    }

    @Test
    fun `활성 미션이 있으면 알람 추적 화면을 유지한다`() {
        assertEquals(
            ActiveMissionRoute,
            resolveAppScreen(
                requestedRoute = ActiveMissionRoute,
                ringingAlarmId = null,
                hasActiveAlarmMission = true,
                authSessionState = AuthSessionState.Authenticated,
            ),
        )
    }

    @Test
    fun `활성 미션이 있어도 일반 화면 요청을 강제로 바꾸지 않는다`() {
        listOf(AppRoute.Home, AppRoute.MyPage, AppRoute.Destination(1L)).forEach { requestedRoute ->
            assertEquals(
                requestedRoute,
                resolveAppScreen(
                    requestedRoute = requestedRoute,
                    ringingAlarmId = null,
                    hasActiveAlarmMission = true,
                    authSessionState = AuthSessionState.Authenticated,
                ),
            )
        }
    }

    @Test
    fun `알람이 울리는 동안 전역 다이얼로그를 표시하지 않는다`() {
        AuthSessionState.entries.forEach { authSessionState ->
            assertEquals(
                false,
                canShowAppDialog(
                    displayedRoute = AppRoute.AlarmRinging("ringing-alarm"),
                    authSessionState = authSessionState,
                ),
            )
        }
    }

    @Test
    fun `일반 비인증 상태에서는 요청한 화면을 유지한다`() {
        assertEquals(
            AppRoute.Home,
            resolveAppScreen(
                requestedRoute = AppRoute.Home,
                ringingAlarmId = null,
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
                displayedRoute = AppRoute.Login,
                authSessionState = AuthSessionState.ReauthenticationRequired,
            ),
        )
    }

    @Test
    fun `인증된 홈 화면에서는 전역 다이얼로그를 표시할 수 있다`() {
        assertEquals(
            true,
            canShowAppDialog(
                displayedRoute = AppRoute.Home,
                authSessionState = AuthSessionState.Authenticated,
            ),
        )
    }

    @Test
    fun `홈 화면에서는 홈 오류를 표시한다`() {
        val message = resolveAppMessage(
            displayedRoute = AppRoute.Home,
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
            displayedRoute = AppRoute.Destination(1L),
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
            displayedRoute = AppRoute.Destination(1L),
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
                displayedRoute = AppRoute.MyPage,
                authSessionState = AuthSessionState.Authenticated,
                homeErrorMessage = "홈 오류",
                alarmSetupErrorMessage = "알람 설정 오류",
                destinationErrorMessage = "목적지 오류",
            ),
        )
    }
}

private val ActiveMissionRoute = AppRoute.ActiveAlarmTracking("occurrence-1")

private val RequestedRoutes = listOf(
    AppRoute.Home,
    AppRoute.MyPage,
    AppRoute.NicknameChange,
    AppRoute.Login,
    AppRoute.TermsAgreement,
    AppRoute.AddAlarm,
    AppRoute.EditAlarm("alarm-1"),
    AppRoute.Destination(1L),
    AppRoute.AlarmSound,
    ActiveMissionRoute,
)
