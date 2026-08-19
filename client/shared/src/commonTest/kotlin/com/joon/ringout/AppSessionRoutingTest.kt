package com.joon.ringout

import com.joon.ringout.domain.auth.AuthSessionState
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSessionRoutingTest {
    @Test
    fun `ordinary unauthenticated session keeps requested screen`() {
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
    fun `reauthentication required session opens login`() {
        assertEquals(
            AppScreen.Login,
            resolveAppScreen(
                requestedScreen = AppScreen.Destination,
                hasRingingAlarm = false,
                hasActiveAlarmMission = false,
                authSessionState = AuthSessionState.ReauthenticationRequired,
            ),
        )
    }

    @Test
    fun `ringing alarm remains visible before login`() {
        assertEquals(
            AppScreen.AlarmRinging,
            resolveAppScreen(
                requestedScreen = AppScreen.Home,
                hasRingingAlarm = true,
                hasActiveAlarmMission = false,
                authSessionState = AuthSessionState.ReauthenticationRequired,
            ),
        )
    }

    @Test
    fun `login opens after ringing alarm ends while reauthentication is required`() {
        assertEquals(
            AppScreen.Login,
            resolveAppScreen(
                requestedScreen = AppScreen.ActiveAlarmTracking,
                hasRingingAlarm = false,
                hasActiveAlarmMission = true,
                authSessionState = AuthSessionState.ReauthenticationRequired,
            ),
        )
    }

    @Test
    fun `reauthentication hides global errors over login`() {
        assertEquals(
            false,
            canShowAppDialog(
                screen = AppScreen.Login,
                authSessionState = AuthSessionState.ReauthenticationRequired,
            ),
        )
    }

    @Test
    fun `authenticated home can show global errors`() {
        assertEquals(
            true,
            canShowAppDialog(
                screen = AppScreen.Home,
                authSessionState = AuthSessionState.Authenticated,
            ),
        )
    }
}
