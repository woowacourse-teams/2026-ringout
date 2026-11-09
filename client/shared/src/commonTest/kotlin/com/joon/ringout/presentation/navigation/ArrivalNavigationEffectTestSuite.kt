package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import com.joon.ringout.domain.push.RoomActivityPushRequest
import com.joon.ringout.domain.push.RoomActivityPushTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The same effect contract runs with Android and iOS Compose runtimes. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalNavigationEffectTestSuite {
    fun `미션이 먼저 사라져도 성공 결과가 저장되기 전에는 도착 화면을 열지 않는다`() = runTest {
        val navigation = AppNavigationState()
        var mission by mutableStateOf<String?>("mission-a")
        var arrival by mutableStateOf<String?>(null)
        withEffectComposition(content = {
            ActiveMissionNavigationEffect(
                activeMissionOccurrenceId = mission,
                navigationState = navigation,
                pendingArrivalOccurrenceId = arrival,
            )
        }) { drain ->
            assertEquals(AppRoute.ActiveAlarmTracking("mission-a"), navigation.requestedRoute)
            mission = null
            drain()
            assertEquals(AppRoute.Home, navigation.requestedRoute)

            // A timeout/force-end would remain here; only a persisted success opens arrival.
            arrival = "mission-a"
            drain()
            assertEquals(listOf(AppRoute.Home, AppRoute.Arrival("mission-a")), navigation.backStack.toList())
        }
    }

    fun `재시작과 반복 알림에서도 최신 미확인 결과 화면을 하나만 유지한다`() = runTest {
        val restored = AppRoute.Arrival("arrival-a")
        val navigation = AppNavigationState(NavBackStack(AppRoute.Home, restored))
        var arrival by mutableStateOf<String?>("arrival-a")
        withEffectComposition(content = {
            ActiveMissionNavigationEffect(
                activeMissionOccurrenceId = null,
                navigationState = navigation,
                pendingArrivalOccurrenceId = arrival,
            )
        }) { drain ->
            repeat(2) {
                navigation.navigate(AppRoute.Arrival("arrival-a"))
                drain()
                assertEquals(listOf(AppRoute.Home, restored), navigation.backStack.toList())
            }

            arrival = "arrival-b"
            drain()
            assertEquals(listOf(AppRoute.Home, AppRoute.Arrival("arrival-b")), navigation.backStack.toList())

            // An unrelated completion cannot replace the pending result before acknowledgement.
            navigation.navigate(AppRoute.Home)
            drain()
            assertEquals(AppRoute.Arrival("arrival-b"), navigation.requestedRoute)
        }
    }

    fun `새 알람과 진행 중 미션을 먼저 표시하고 끝나면 미확인 결과로 돌아온다`() = runTest {
        val navigation = AppNavigationState()
        var ringing by mutableStateOf(false)
        var mission by mutableStateOf<String?>(null)
        var arrival by mutableStateOf<String?>("arrival-a")
        withEffectComposition(content = {
            ActiveMissionNavigationEffect(
                activeMissionOccurrenceId = mission,
                navigationState = navigation,
                isRingingAlarm = ringing,
                pendingArrivalOccurrenceId = arrival,
            )
        }) { drain ->
            assertEquals(AppRoute.Arrival("arrival-a"), navigation.requestedRoute)
            ringing = true
            mission = "mission-b"
            arrival = "arrival-b"
            drain()
            // The platform ringing surface stays above the unmodified underlying stack.
            assertEquals(AppRoute.Arrival("arrival-a"), navigation.requestedRoute)

            ringing = false
            drain()
            assertEquals(AppRoute.ActiveAlarmTracking("mission-b"), navigation.requestedRoute)
            mission = null
            drain()
            assertEquals(listOf(AppRoute.Home, AppRoute.Arrival("arrival-b")), navigation.backStack.toList())
        }
    }

    fun `첫 조회가 끝나기 전에는 복원된 도착 화면과 푸시 요청을 유지한다`() = runTest {
        val restored = AppRoute.Arrival("arrival-a")
        val navigation = AppNavigationState(NavBackStack(AppRoute.Home, restored))
        var loaded by mutableStateOf(false)
        var arrival by mutableStateOf<String?>(null)
        var push by mutableStateOf<RoomActivityPushRequest?>(pushRequest())
        val handled = mutableListOf<String>()
        withEffectComposition(content = {
            ActiveMissionNavigationEffect(
                activeMissionOccurrenceId = null,
                navigationState = navigation,
                pendingRoomActivityPush = push,
                onRoomActivityPushHandled = { handled += it; push = null },
                pendingArrivalOccurrenceId = arrival,
                isArrivalStateLoaded = loaded,
            )
        }) { drain ->
            assertEquals(listOf(AppRoute.Home, restored), navigation.backStack.toList())
            assertEquals(emptyList(), handled)

            arrival = "arrival-a"
            loaded = true
            drain()
            assertEquals(restored, navigation.requestedRoute)
            assertEquals(emptyList(), handled)

            arrival = null
            drain()
            assertEquals(AppRoute.RoomActivity("41", "2026-11-09"), navigation.requestedRoute)
            assertEquals(listOf("room-push"), handled)
            assertNull(push)
        }
    }

    fun `도착 결과 확인 후 홈으로 돌아가며 다시 자동 표시하지 않는다`() = runTest {
        val navigation = AppNavigationState()
        var arrival by mutableStateOf<String?>("arrival-a")
        withEffectComposition(content = {
            ActiveMissionNavigationEffect(
                activeMissionOccurrenceId = null,
                navigationState = navigation,
                pendingArrivalOccurrenceId = arrival,
            )
        }) { drain ->
            assertEquals(AppRoute.Arrival("arrival-a"), navigation.requestedRoute)
            arrival = null
            drain()
            assertEquals(listOf(AppRoute.Home), navigation.backStack.toList())
            navigation.navigate(AppRoute.Records)
            drain()
            assertEquals(AppRoute.Records, navigation.requestedRoute)
        }

        withEffectComposition(content = {
            ActiveMissionNavigationEffect(null, navigation, pendingArrivalOccurrenceId = null)
        }) {
            assertEquals(AppRoute.Records, navigation.requestedRoute)
        }
    }

    fun `같은 활성 미션에서 홈으로 나온 선택은 미확인 결과가 있어도 유지한다`() = runTest {
        val navigation = AppNavigationState()
        var mission by mutableStateOf<String?>("mission-a")
        var arrival by mutableStateOf<String?>(null)
        withEffectComposition(content = {
            ActiveMissionNavigationEffect(
                activeMissionOccurrenceId = mission,
                navigationState = navigation,
                pendingArrivalOccurrenceId = arrival,
            )
        }) { drain ->
            navigation.navigate(AppRoute.Home)
            arrival = "earlier-arrival"
            drain()
            assertEquals(AppRoute.Home, navigation.requestedRoute)

            mission = null
            drain()
            assertEquals(AppRoute.Arrival("earlier-arrival"), navigation.requestedRoute)
        }
    }

    fun `미확인 도착 결과를 확인하기 전에는 모임 푸시를 소비하지 않는다`() = runTest {
        val navigation = AppNavigationState()
        var arrival by mutableStateOf<String?>("arrival-a")
        var push by mutableStateOf<RoomActivityPushRequest?>(pushRequest())
        val handled = mutableListOf<String>()
        withEffectComposition(content = {
            ActiveMissionNavigationEffect(
                activeMissionOccurrenceId = null,
                navigationState = navigation,
                pendingRoomActivityPush = push,
                onRoomActivityPushHandled = { handled += it; push = null },
                pendingArrivalOccurrenceId = arrival,
            )
        }) { drain ->
            assertEquals(AppRoute.Arrival("arrival-a"), navigation.requestedRoute)
            assertEquals(emptyList(), handled)

            arrival = "arrival-b"
            drain()
            assertEquals(AppRoute.Arrival("arrival-b"), navigation.requestedRoute)
            assertEquals(emptyList(), handled)

            arrival = null
            drain()
            assertEquals(AppRoute.RoomActivity("41", "2026-11-09"), navigation.requestedRoute)
            assertEquals(listOf("room-push"), handled)
        }
    }

    private fun pushRequest() = RoomActivityPushRequest(
        requestId = "room-push",
        target = requireNotNull(
            RoomActivityPushTarget.parse(mapOf("roomId" to "41", "activityDate" to "2026-11-09")),
        ),
    )
}
