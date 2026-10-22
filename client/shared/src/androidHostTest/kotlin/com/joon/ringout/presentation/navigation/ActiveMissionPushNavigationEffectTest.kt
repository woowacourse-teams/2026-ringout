package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.push.RoomActivityPushRequest
import com.joon.ringout.domain.push.RoomActivityPushTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActiveMissionPushNavigationEffectTest {
    @Test
    fun `인증 복원 중 푸시를 보류하고 활성 미션 이동 뒤 푸시 목적지를 반영한다`() = runTest {
        val navigationState = AppNavigationState()
        var authSessionState by mutableStateOf(AuthSessionState.Restoring)
        var activeMissionOccurrenceId by mutableStateOf<String?>("occurrence-1")
        var pendingRequest by mutableStateOf<RoomActivityPushRequest?>(
            pushRequest("click-a", "41", "2026-10-07"),
        )
        val handledRequestIds = mutableListOf<String>()

        withEffectComposition(
            content = {
                ActiveMissionNavigationEffect(
                    activeMissionOccurrenceId = activeMissionOccurrenceId,
                    navigationState = navigationState,
                    authSessionState = authSessionState,
                    pendingRoomActivityPush = pendingRequest,
                    onRoomActivityPushHandled = { requestId ->
                        handledRequestIds += requestId
                        pendingRequest = null
                    },
                )
            },
        ) { drain ->
            val missionRoute = AppRoute.ActiveAlarmTracking("occurrence-1")
            assertEquals(missionRoute, navigationState.requestedRoute)
            assertEquals(listOf(AppRoute.Home, missionRoute), navigationState.backStack.toList())
            assertEquals(emptyList(), handledRequestIds)
            assertEquals("click-a", pendingRequest?.requestId)

            authSessionState = AuthSessionState.Authenticated
            drain()

            assertEquals(
                listOf(
                    AppRoute.Home,
                    AppRoute.Social,
                    AppRoute.RoomHome("41"),
                    AppRoute.RoomActivity("41", "2026-10-07"),
                ),
                navigationState.backStack.toList(),
            )
            assertEquals(listOf("click-a"), handledRequestIds)
            assertNull(pendingRequest)
        }
    }

    @Test
    fun `알람 울림 중에는 푸시 요청을 보존하고 울림 종료 후에 처리한다`() = runTest {
        val navigationState = AppNavigationState()
        var isRingingAlarm by mutableStateOf(true)
        var pendingRequest by mutableStateOf<RoomActivityPushRequest?>(
            pushRequest("click-b", "42", "2026-10-08"),
        )
        val handledRequestIds = mutableListOf<String>()

        withEffectComposition(
            content = {
                ActiveMissionNavigationEffect(
                    activeMissionOccurrenceId = null,
                    navigationState = navigationState,
                    pendingRoomActivityPush = pendingRequest,
                    isRingingAlarm = isRingingAlarm,
                    onRoomActivityPushHandled = { requestId ->
                        handledRequestIds += requestId
                        pendingRequest = null
                    },
                )
            },
        ) { drain ->
            assertEquals(AppRoute.Home, navigationState.requestedRoute)
            assertEquals(emptyList(), handledRequestIds)
            assertEquals("click-b", pendingRequest?.requestId)

            isRingingAlarm = false
            drain()

            assertEquals(AppRoute.RoomActivity("42", "2026-10-08"), navigationState.requestedRoute)
            assertEquals(listOf("click-b"), handledRequestIds)
            assertNull(pendingRequest)
        }
    }

    @Test
    fun `비로그인과 재인증 상태에서는 푸시를 이동 없이 폐기한다`() = runTest {
        val navigationState = AppNavigationState()
        var authSessionState by mutableStateOf(AuthSessionState.Unauthenticated)
        var pendingRequest by mutableStateOf<RoomActivityPushRequest?>(
            pushRequest("click-c", "43", "2026-10-09"),
        )
        val handledRequestIds = mutableListOf<String>()

        withEffectComposition(
            content = {
                ActiveMissionNavigationEffect(
                    activeMissionOccurrenceId = null,
                    navigationState = navigationState,
                    authSessionState = authSessionState,
                    pendingRoomActivityPush = pendingRequest,
                    onRoomActivityPushHandled = { requestId ->
                        handledRequestIds += requestId
                        pendingRequest = null
                    },
                )
            },
        ) { drain ->
            assertEquals(listOf("click-c"), handledRequestIds)
            assertNull(pendingRequest)
            assertEquals(listOf(AppRoute.Home), navigationState.backStack.toList())

            authSessionState = AuthSessionState.ReauthenticationRequired
            pendingRequest = pushRequest("click-d", "44", "2026-10-10")
            drain()

            assertEquals(listOf("click-c", "click-d"), handledRequestIds)
            assertNull(pendingRequest)
            assertEquals(listOf(AppRoute.Home), navigationState.backStack.toList())
        }
    }

    private fun TestScope.pushRequest(requestId: String, roomId: String, activityDate: String) =
        RoomActivityPushRequest(
            requestId,
            requireNotNull(
                RoomActivityPushTarget.parse(
                    mapOf("roomId" to roomId, "activityDate" to activityDate),
                ),
            ),
        )
}
