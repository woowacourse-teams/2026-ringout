package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.FakeRoomScheduleClock
import com.joon.ringout.domain.room.RoomMemberDetails
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.RoomSummary
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RoomHomeLoadTest {
    @Test
    fun `복원 중에는 상세 조회를 보류하고 인증 확정 뒤 한 번 조회한다`() = runTest {
        val authSession = AuthSession()
        var requests = 0
        val viewModel = RoomHomeViewModel(
            coroutineScope = this,
            loadRoom = { roomId ->
                requests += 1
                roomDetails(roomId)
            },
            authSession = authSession,
        )

        viewModel.onRouteVisible("7", AuthSessionState.Restoring, null)
        runCurrent()

        assertEquals(0, requests)
        assertTrue(viewModel.uiState.value.isLoading)

        authSession.startNewSession()
        viewModel.onAuthSessionChanged(authSession.state.value, authSession.identity.value)
        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        assertEquals(1, requests)
        assertEquals("서버 모임 7", viewModel.uiState.value.room?.name)
    }

    @Test
    fun `상세 응답을 화면에 표시하고 같은 진입과 탭 날짜 변경은 추가 조회하지 않는다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val clock = FakeRoomScheduleClock(elapsedMillis = 8 * 3_600_000L + 15 * 60_000L)
        val roomIds = mutableListOf<Long>()
        val viewModel = RoomHomeViewModel(
            coroutineScope = this,
            clock = clock,
            loadRoom = { roomId ->
                roomIds += roomId
                roomDetails(roomId)
            },
            authSession = authSession,
        )
        val identity = authSession.identity.value

        viewModel.onRouteVisible("7", AuthSessionState.Authenticated, identity)
        viewModel.onRouteVisible("7", AuthSessionState.Authenticated, identity)
        runCurrent()

        val loaded = viewModel.uiState.value
        assertEquals(listOf(7L), roomIds)
        assertEquals("서버 모임 7", loaded.room?.name)
        assertEquals("서버 소개", loaded.room?.description)
        assertEquals(19, loaded.room?.participantCount)
        assertEquals("오전 8:15", loaded.room?.activityTimeText)
        assertEquals(listOf("월", "수"), loaded.room?.activityDays)
        assertEquals(RoomMembershipRole.MEMBER, loaded.membershipRole)
        assertEquals(listOf("11", "10"), loaded.members.map { it.id })
        assertEquals(listOf("두 번째", "첫 번째"), loaded.members.map { it.nickname })
        assertEquals("https://cdn.example.com/member.png", loaded.members.first().profileImageUrl)
        assertNull(loaded.members.last().profileImageUrl)
        assertTrue(loaded.areMembersLoaded)
        assertFalse(loaded.recordsState.isDataLoaded)
        assertEquals(MissionDate.parse("2026-09-28"), loaded.ongoingActivity?.date)
        assertEquals(19, loaded.ongoingActivity?.participantCount)

        val selectedDate = MissionDate.parse("2026-10-02")
        viewModel.onTabSelected(RoomHomeTab.Records)
        viewModel.onDateSelected(selectedDate)
        viewModel.onRouteVisible("7", AuthSessionState.Authenticated, identity)
        runCurrent()

        assertEquals(listOf(7L), roomIds)
        assertEquals(RoomHomeTab.Records, viewModel.uiState.value.selectedTab)
        assertEquals(selectedDate, viewModel.uiState.value.recordsState.selectedDate)
        assertFalse(viewModel.uiState.value.recordsState.isDataLoaded)

        assertEquals(
            RoomHomeActivityDestination("7", MissionDate.parse("2026-09-28")),
            viewModel.activityDestination("7", MissionDate.parse("2026-09-28"), identity),
        )
        assertNull(viewModel.activityDestination("7", MissionDate.parse("2026-10-02"), identity))
        clock.elapsedMillis += 60 * 60_000L
        assertNull(viewModel.activityDestination("7", MissionDate.parse("2026-09-28"), identity))
        assertNull(viewModel.uiState.value.ongoingActivity)
    }

    @Test
    fun `통신 오류 재시도는 같은 방 상세 GET만 다시 요청한다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val roomIds = mutableListOf<Long>()
        val viewModel = RoomHomeViewModel(
            coroutineScope = this,
            loadRoom = { roomId ->
                roomIds += roomId
                if (roomIds.size == 1) throw RoomRepositoryException(500, "ROOM500", "실패")
                roomDetails(roomId)
            },
            authSession = authSession,
        )

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()
        assertTrue(viewModel.uiState.value.canRetry)
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onRetry()
        runCurrent()

        assertEquals(listOf(7L, 7L), roomIds)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("서버 모임 7", viewModel.uiState.value.room?.name)
    }

    @Test
    fun `인증 참여 권한 또는 삭제 오류는 자동으로 재시도하지 않는다`() = runTest {
        val failures = listOf(
            RoomRepositoryException(401, "ROOM401", "인증 필요") to "로그인이 필요해요",
            RoomRepositoryException(403, "ROOM403", "참여하지 않음") to "참여하고 있지 않아요",
            RoomRepositoryException(404, "ROOM404", "삭제됨") to "삭제됐어요",
        )

        failures.forEach { (failure, expectedMessage) ->
            val authSession = AuthSession().apply { startNewSession() }
            var requests = 0
            val viewModel = RoomHomeViewModel(
                coroutineScope = this,
                loadRoom = {
                    requests += 1
                    throw failure
                },
                authSession = authSession,
            )

            viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
            runCurrent()
            viewModel.onRetry()
            runCurrent()

            assertEquals(1, requests)
            assertFalse(viewModel.uiState.value.canRetry)
            assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains(expectedMessage))
        }
    }

    @Test
    fun `계정 변경 뒤 늦게 끝난 이전 상세 응답은 새 계정의 화면을 덮어쓰지 않는다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        var finishOldRequest: Continuation<RoomMembershipDetails>? = null
        var requests = 0
        val viewModel = RoomHomeViewModel(
            coroutineScope = this,
            loadRoom = { roomId ->
                requests += 1
                if (requests == 1) suspendCoroutine { finishOldRequest = it }
                else roomDetails(roomId, name = "새 계정 모임")
            },
            authSession = authSession,
        )

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()
        authSession.startNewSession()
        viewModel.onAuthSessionChanged(authSession.state.value, authSession.identity.value)
        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        assertEquals("새 계정 모임", viewModel.uiState.value.room?.name)
        finishOldRequest?.resume(roomDetails(7, name = "이전 계정 모임"))
        runCurrent()

        assertEquals(2, requests)
        assertEquals("새 계정 모임", viewModel.uiState.value.room?.name)
    }

    @Test
    fun `세션이 사라지면 상세 회원 기록과 진행 중 활동을 비운다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val viewModel = RoomHomeViewModel(
            initialState = RoomHomeUiState(
                room = RoomUiModel(
                    id = "7",
                    name = "세션 모임",
                    description = "서버 소개",
                    createdAt = "2026-10-01",
                    activityDays = listOf("월"),
                    activityTimeText = "오전 8:00",
                    participantCount = 1,
                    isJoined = true,
                ),
                membershipRole = RoomMembershipRole.OWNER,
                members = listOf(RoomHomeMemberUiModel("10", "이전 회원")),
                areMembersLoaded = true,
                ongoingActivity = RoomHomeOngoingActivityUiModel(MissionDate.parse("2026-10-01"), 1),
            ),
            authSession = authSession,
        )

        authSession.clear()
        viewModel.onAuthSessionChanged(authSession.state.value, authSession.identity.value)

        val state = viewModel.uiState.value
        assertNull(state.room)
        assertNull(state.membershipRole)
        assertTrue(state.members.isEmpty())
        assertFalse(state.areMembersLoaded)
        assertFalse(state.recordsState.isDataLoaded)
        assertTrue(state.recordsState.records.isEmpty())
        assertNull(state.ongoingActivity)
    }
}

private fun roomDetails(roomId: Long, name: String = "서버 모임 $roomId") = RoomMembershipDetails(
    room = RoomSummary(
        id = roomId,
        name = name,
        description = "서버 소개",
        imageUrl = null,
        activityDays = listOf("WEDNESDAY", "MONDAY"),
        activityTime = "08:15",
        memberCount = 19,
        isJoined = true,
        createdAt = "2026-10-01T08:30:00",
    ),
    membershipRole = RoomMembershipRole.MEMBER,
    members = listOf(
        RoomMemberDetails(11L, "두 번째", "https://cdn.example.com/member.png"),
        RoomMemberDetails(10L, "첫 번째"),
    ),
)
