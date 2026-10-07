package com.joon.ringout.presentation.app

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.RoomMemberMovement
import com.joon.ringout.domain.room.RoomMemberMovementStatus
import com.joon.ringout.presentation.roomactivity.RoomActivityViewModel
import com.joon.ringout.presentation.roomhome.RoomHomeMemberUiModel
import com.joon.ringout.presentation.roomhome.RoomHomeUiState
import com.joon.ringout.presentation.roomhome.RoomHomeViewModel
import com.joon.ringout.presentation.roomlist.RoomListViewModel
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionCoordinatorTest {
    @Test
    fun `중앙 세션 바인딩은 가려진 RoomActivity 데이터도 계정 변경 시 지운다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val roomActivityViewModel = RoomActivityViewModel(
            loadMembers = {
                listOf(RoomMemberMovement(5, "이전 회원", RoomMemberMovementStatus.Moving))
            },
            authSession = authSession,
            coroutineScope = this,
        )
        roomActivityViewModel.onRouteVisible(
            "41",
            MissionDate.of(2026, 10, 7).iso8601,
            authSession.state.value,
            authSession.identity.value,
        )
        roomActivityViewModel.onResume()
        runCurrent()
        assertEquals(listOf("이전 회원"), roomActivityViewModel.uiState.value.members.map { it.nickname })

        val handler = RoomSessionStateHandler(null, emptyList(), listOf(roomActivityViewModel))
        authSession.startNewSession()
        handler.onSessionStateChanged(authSession.state.value, authSession.identity.value)

        assertEquals(emptyList(), roomActivityViewModel.uiState.value.members)
        assertFalse(roomActivityViewModel.uiState.value.isDataLoaded)
    }

    @Test
    fun `중앙 세션 바인딩은 유지된 소셜과 RoomHome 데이터를 계정 변경에 맞춰 갱신한다`() = runTest {
        var roomListRequests = 0
        val roomListViewModel = RoomListViewModel(
            loadRooms = {
                roomListRequests += 1
                Result.success(listOf(previewRoom))
            },
            coroutineScope = this,
        )
        val roomHomeViewModel = RoomHomeViewModel(
            initialState = RoomHomeUiState(
                room = previewRoom,
                members = listOf(RoomHomeMemberUiModel(id = "1", nickname = "이전 회원")),
                areMembersLoaded = true,
            ),
        )
        val handler = RoomSessionStateHandler(roomListViewModel, listOf(roomHomeViewModel))

        handler.onSessionStateChanged(AuthSessionState.Authenticated, Any())
        runCurrent()
        handler.onSessionStateChanged(AuthSessionState.Authenticated, Any())
        runCurrent()

        assertEquals(2, roomListRequests)
        assertEquals(listOf(previewRoom), roomListViewModel.uiState.allRooms)
        assertNull(roomHomeViewModel.uiState.value.room)
        assertEquals(emptyList(), roomHomeViewModel.uiState.value.members)
    }

    private companion object {
        val previewRoom = RoomUiModel(
            id = "room-1",
            name = "아침 모임",
            description = "모임 소개",
            createdAt = "2026-10-01",
            activityDays = listOf("월"),
            activityTimeText = "오전 08:00",
            participantCount = 1,
            isJoined = true,
        )
    }
}
