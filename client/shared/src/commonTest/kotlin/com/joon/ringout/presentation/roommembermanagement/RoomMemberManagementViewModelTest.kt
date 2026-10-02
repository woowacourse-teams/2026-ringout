package com.joon.ringout.presentation.roommembermanagement

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.room.RoomMemberDetails
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.RoomSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RoomMemberManagementViewModelTest {
    @Test
    fun `방장은 요청한 모임의 서버 회원 목록을 조회해서 표시한다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val requestedRoomIds = mutableListOf<Long>()
        val viewModel = RoomMemberManagementViewModel(
            loadRoom = { roomId ->
                requestedRoomIds += roomId
                roomDetails(roomId)
            },
            authSession = authSession,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf(7L), requestedRoomIds)
        assertTrue(state.canManageMembers)
        assertFalse(state.canRemoveMembers)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(listOf("11", "10"), state.members.map { it.id })
        assertEquals(listOf("두 번째", "첫 번째"), state.members.map { it.nickname })
        assertEquals("https://cdn.example.com/member.png", state.members.first().profileImageUrl)
        assertTrue(state.members.none { it.isOwner })
        assertTrue(state.members.all { it.joinedDate == null })
    }

    @Test
    fun `회원은 목록과 추방 액션을 표시하지 않고 권한 없음으로 닫는다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val viewModel = RoomMemberManagementViewModel(
            loadRoom = { roomDetails(it, role = RoomMembershipRole.MEMBER) },
            authSession = authSession,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.canManageMembers)
        assertFalse(state.canRemoveMembers)
        assertTrue(state.members.isEmpty())
        assertEquals("방장만 회원을 관리할 수 있어요.", state.errorMessage)
    }

    @Test
    fun `인증되지 않은 상태에서는 서버 조회 없이 로그인 안내를 표시한다`() = runTest {
        val authSession = AuthSession()
        var requests = 0
        val viewModel = RoomMemberManagementViewModel(
            loadRoom = {
                requests += 1
                roomDetails(it)
            },
            authSession = authSession,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", AuthSessionState.Unauthenticated, null)
        runCurrent()

        assertEquals(0, requests)
        assertFalse(viewModel.uiState.value.canManageMembers)
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("로그인이 필요해요"))
    }

    @Test
    fun `요청 방과 다른 상세 응답은 목록을 표시하지 않는다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val viewModel = RoomMemberManagementViewModel(
            loadRoom = { roomDetails(9) },
            authSession = authSession,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.canManageMembers)
        assertTrue(state.members.isEmpty())
        assertTrue(state.errorMessage.orEmpty().contains("회원 목록을 불러오지 못했어요"))
    }

    @Test
    fun `중복된 회원 식별자가 있으면 서버 목록을 신뢰하지 않는다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val viewModel = RoomMemberManagementViewModel(
            loadRoom = { roomDetails(it, members = listOf(serverMember(11), serverMember(11))) },
            authSession = authSession,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.canManageMembers)
        assertTrue(state.members.isEmpty())
        assertTrue(state.errorMessage.orEmpty().contains("회원 정보를 불러오지 못했어요"))
    }

    @Test
    fun `실제 경로에서는 추방 확인을 눌러도 회원 목록을 로컬에서 제거하지 않는다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val viewModel = RoomMemberManagementViewModel(
            loadRoom = { roomDetails(it) },
            authSession = authSession,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        val loadedMembers = viewModel.uiState.value.members
        viewModel.onRemoveMemberClick(loadedMembers.first().id)
        viewModel.onConfirmRemove()

        assertEquals(loadedMembers, viewModel.uiState.value.members)
        assertNull(viewModel.uiState.value.selectedMember)
    }

    @Test
    fun `계정 변경 뒤 늦게 끝난 이전 요청은 새 계정의 목록을 덮어쓰지 않는다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        var finishOldRequest: Continuation<RoomMembershipDetails>? = null
        var requests = 0
        val viewModel = RoomMemberManagementViewModel(
            loadRoom = { roomId ->
                requests += 1
                if (requests == 1) suspendCoroutine { finishOldRequest = it }
                else roomDetails(roomId, members = listOf(serverMember(20, "새 계정 회원")))
            },
            authSession = authSession,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()
        authSession.startNewSession()
        viewModel.onAuthSessionChanged(authSession.state.value, authSession.identity.value)
        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        assertEquals(listOf("새 계정 회원"), viewModel.uiState.value.members.map { it.nickname })
        finishOldRequest?.resume(roomDetails(7, members = listOf(serverMember(30, "이전 계정 회원"))))
        runCurrent()

        assertEquals(2, requests)
        assertEquals(listOf("새 계정 회원"), viewModel.uiState.value.members.map { it.nickname })
    }

    @Test
    fun `권한 오류는 회원 목록을 비우고 권한 없음 메시지를 표시한다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val viewModel = RoomMemberManagementViewModel(
            loadRoom = { throw RoomRepositoryException(403, "ROOM403", "권한 없음") },
            authSession = authSession,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.canManageMembers)
        assertTrue(state.members.isEmpty())
        assertEquals("방장만 회원을 관리할 수 있어요.", state.errorMessage)
    }

    private fun roomDetails(
        roomId: Long,
        role: RoomMembershipRole = RoomMembershipRole.OWNER,
        members: List<RoomMemberDetails> = listOf(
            serverMember(11, "두 번째", "https://cdn.example.com/member.png"),
            serverMember(10, "첫 번째"),
        ),
    ) = RoomMembershipDetails(
        room = RoomSummary(
            id = roomId,
            name = "서버 모임 $roomId",
            description = "서버 소개",
            imageUrl = null,
            activityDays = listOf("MONDAY"),
            activityTime = "08:15",
            memberCount = members.size,
            isJoined = true,
            createdAt = "2026-10-01T08:30:00",
        ),
        membershipRole = role,
        members = members,
    )

    private fun serverMember(
        id: Long,
        nickname: String = "회원 $id",
        profileImageUrl: String? = null,
    ) = RoomMemberDetails(
        userId = id,
        nickname = nickname,
        profileImageUrl = profileImageUrl,
    )
}
