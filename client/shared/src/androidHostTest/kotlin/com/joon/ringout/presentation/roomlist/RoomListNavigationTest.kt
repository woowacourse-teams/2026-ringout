package com.joon.ringout.presentation.roomlist

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.connectivity.NetworkMonitor
import com.joon.ringout.domain.connectivity.NetworkStatus
import com.joon.ringout.presentation.navigation.AppNavigationState
import com.joon.ringout.presentation.navigation.AppRoute
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h947dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomListNavigationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `하단 목록에서 가입한 모임을 누르면 소개 없이 모임 홈으로 이동하고 목록으로 돌아온다`() =
        withRoomList { fixture ->
            clickAllRoom(JoinedRoom)

            assertDestinationAndReturn(fixture, AppRoute.RoomHome(JoinedRoom.id))
        }

    @Test
    fun `하단 목록에서 가입하지 않은 모임을 누르면 소개로 이동하고 목록으로 돌아온다`() =
        withRoomList { fixture ->
            clickAllRoom(UnjoinedRoom)

            assertDestinationAndReturn(fixture, AppRoute.RoomDetail(UnjoinedRoom.id))
        }

    @Test
    fun `상단 가입 모임 바로가기는 기존처럼 모임 홈으로 이동한다`() =
        withRoomList { fixture ->
            compose.onNode(
                hasText(JoinedRoom.name) and !hasText("${JoinedRoom.participantCount}명 참여중"),
            ).performClick()

            assertDestinationAndReturn(fixture, AppRoute.RoomHome(JoinedRoom.id))
        }

    @Test
    fun `가입과 탈퇴가 목록에 반영되면 같은 모임의 이동 화면도 바뀐다`() =
        withRoomList { fixture ->
            clickAllRoom(UnjoinedRoom)
            assertDestinationAndReturn(fixture, AppRoute.RoomDetail(UnjoinedRoom.id))

            compose.runOnIdle {
                fixture.rooms = fixture.rooms.map { room ->
                    if (room.id == UnjoinedRoom.id) room.copy(isJoined = true) else room
                }
                fixture.viewModel.onRetryRooms()
            }
            clickAllRoom(UnjoinedRoom)
            assertDestinationAndReturn(fixture, AppRoute.RoomHome(UnjoinedRoom.id))

            compose.runOnIdle {
                fixture.rooms = fixture.rooms.map { room ->
                    if (room.id == UnjoinedRoom.id) room.copy(isJoined = false) else room
                }
                fixture.viewModel.onRoomLeft(UnjoinedRoom.id.toLong(), fixture.sessionIdentity)
            }
            clickAllRoom(UnjoinedRoom)
            assertDestinationAndReturn(fixture, AppRoute.RoomDetail(UnjoinedRoom.id))
        }

    private fun clickAllRoom(room: RoomUiModel) {
        // 상단 바로가기와 이름이 같으므로 하단 카드에만 있는 참여 인원으로 구분한다.
        compose.onNode(
            hasText(room.name) and hasText("${room.participantCount}명 참여중"),
        ).performClick()
    }

    private fun assertDestinationAndReturn(fixture: Fixture, destination: AppRoute) {
        compose.runOnIdle {
            assertEquals(
                listOf(AppRoute.Home, AppRoute.Social, destination),
                fixture.navigation.backStack.toList(),
            )
            fixture.navigation.popBackStack(destination)
            assertEquals(listOf(AppRoute.Home, AppRoute.Social), fixture.navigation.backStack.toList())
        }
        compose.onNodeWithText("모임").assertExists()
    }

    private fun withRoomList(verify: (Fixture) -> Unit) {
        val fixture = Fixture()
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    RoomListRoute(
                        viewModel = fixture.viewModel,
                        networkMonitor = fixture.networkMonitor,
                        authSessionState = AuthSessionState.Authenticated,
                        sessionIdentity = fixture.sessionIdentity,
                        onCreateRoom = {},
                        onLoginClick = {},
                        onRoomClick = { fixture.navigation.navigate(AppRoute.RoomDetail(it)) },
                        onJoinedRoomClick = { fixture.navigation.navigate(AppRoute.RoomHome(it)) },
                    )
                }
            }
            compose.waitForIdle()
            verify(fixture)
        } finally {
            activity.pause().stop().destroy()
            fixture.scope.cancel()
        }
    }

    private class Fixture {
        val sessionIdentity = Any()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val networkMonitor = object : NetworkMonitor {
            override val status = MutableStateFlow(NetworkStatus.Online)
        }
        var rooms = listOf(JoinedRoom, UnjoinedRoom)
        val viewModel = RoomListViewModel(
            loadRooms = { Result.success(rooms) },
            coroutineScope = scope,
        )
        val navigation = AppNavigationState().apply { navigate(AppRoute.Social) }
    }
}

private val JoinedRoom = RoomUiModel(
    id = "1",
    name = "아침 러닝 모임",
    description = "평일 아침에 함께 달려요.",
    createdAt = "2026-10-01",
    activityDays = listOf("월", "수", "금"),
    activityTimeText = "오전 7:00",
    participantCount = 6,
    isJoined = true,
)

private val UnjoinedRoom = JoinedRoom.copy(
    id = "2",
    name = "주말 산책 모임",
    participantCount = 3,
    isJoined = false,
)
