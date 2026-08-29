package com.joon.ringout.presentation.app

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.common.AppMessageSource
import com.joon.ringout.presentation.destination.DestinationSelection
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.navigation.AppNavigationState
import com.joon.ringout.presentation.navigation.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppRuntimeHandlersTest {
    @Test
    fun `세션 상태에 맞는 화면 상태 변경만 전달한다`() {
        val expectedEvents = mapOf(
            AuthSessionState.Restoring to listOf("sessionRestoring"),
            AuthSessionState.Unauthenticated to listOf("loggedOut", "destinationLoggedOut"),
            AuthSessionState.Authenticated to listOf("authenticated"),
            AuthSessionState.ReauthenticationRequired to
                listOf("loggedOut", "destinationLoggedOut"),
        )

        expectedEvents.forEach { (state, expected) ->
            val events = mutableListOf<String>()
            val handler = AuthSessionStateHandler(
                onSessionRestoring = { events += "sessionRestoring" },
                onLoggedOut = { events += "loggedOut" },
                onAuthenticated = { events += "authenticated" },
                onDestinationLoggedOut = { events += "destinationLoggedOut" },
            )

            handler.onSessionStateChanged(state)

            assertEquals(expected, events, "세션 상태: $state")
        }
    }

    @Test
    fun `메시지 출처에 맞는 오류만 해제한다`() {
        val expectedEvents = mapOf(
            AppMessageSource.Home to listOf("home"),
            AppMessageSource.AlarmSetup to listOf("alarmSetup"),
            AppMessageSource.Destination to listOf("destination"),
            null to emptyList(),
        )

        expectedEvents.forEach { (source, expected) ->
            val events = mutableListOf<String>()
            val handler = AppMessageDismissHandler(
                onHomeErrorDismissed = { events += "home" },
                onAlarmSetupErrorDismissed = { events += "alarmSetup" },
                onDestinationErrorDismissed = { events += "destination" },
            )

            handler.dismiss(source)

            assertEquals(expected, events, "메시지 출처: $source")
        }
    }

    @Test
    fun `현재 편집기의 저장 성공만 처리하고 홈으로 이동한다`() {
        val navigationState = AppNavigationState().apply { navigate(AppRoute.AddAlarm) }
        val alarmSetupViewModel = alarmSetupViewModelWithPendingSave()
        val request = requireNotNull(alarmSetupViewModel.uiState.pendingSaveRequest)
        val callbacks = AppAlarmControllerCallbacks(
            navigationState = navigationState,
            editorRoute = AppRoute.AddAlarm,
            alarmSetupViewModel = alarmSetupViewModel,
            homeViewModel = HomeViewModel(),
        )

        callbacks.onSaveCompleted(request)

        assertEquals(AppRoute.Home, navigationState.requestedRoute)
        assertNull(alarmSetupViewModel.uiState.pendingSaveRequest)
    }

    @Test
    fun `이미 벗어난 편집기의 저장 결과는 무시한다`() {
        val navigationState = AppNavigationState().apply { navigate(AppRoute.AddAlarm) }
        val alarmSetupViewModel = alarmSetupViewModelWithPendingSave()
        val request = requireNotNull(alarmSetupViewModel.uiState.pendingSaveRequest)
        val callbacks = AppAlarmControllerCallbacks(
            navigationState = navigationState,
            editorRoute = AppRoute.AddAlarm,
            alarmSetupViewModel = alarmSetupViewModel,
            homeViewModel = HomeViewModel(),
        )
        val currentRoute = AppRoute.EditAlarm("another-alarm")
        navigationState.navigate(currentRoute)

        callbacks.onSaveCompleted(request)
        callbacks.onSaveError(request, "저장 실패")

        assertEquals(currentRoute, navigationState.requestedRoute)
        assertSame(request, alarmSetupViewModel.uiState.pendingSaveRequest)
        assertNull(alarmSetupViewModel.uiState.errorMessage)
    }

    @Test
    fun `일반 알람 오류는 홈 오류 상태로 전달한다`() {
        val homeViewModel = HomeViewModel()
        val callbacks = AppAlarmControllerCallbacks(
            navigationState = AppNavigationState(),
            editorRoute = null,
            alarmSetupViewModel = null,
            homeViewModel = homeViewModel,
        )

        callbacks.onError("알람 오류")

        assertEquals("알람 오류", homeViewModel.uiState.errorMessage)
    }
}

private fun alarmSetupViewModelWithPendingSave(): AlarmSetupViewModel =
    AlarmSetupViewModel(createAlarmId = { "alarm-1" }).apply {
        startCreating(initialTime = "07:30")
        updateDestination(
            DestinationSelection(
                name = "회사",
                address = "서울특별시 강남구 테헤란로 1",
                latitude = 37.4979,
                longitude = 127.0276,
            ),
        )
        assertTrue(requestSave())
    }
