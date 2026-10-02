package com.joon.ringout.presentation.roommembermanagement

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.room.RoomManagementMember
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RoomMemberManagementViewModelTest {
    @Test
    fun `회원 관리 조회 결과의 순서 역할 가입일 프로필을 화면에 표시한다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val requestedRoomIds = mutableListOf<Long>()
        val members = members()
        val viewModel = RoomMemberManagementViewModel(
            loadMembers = { roomId ->
                requestedRoomIds += roomId
                members
            },
            authSession = authSession,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf(7L), requestedRoomIds)
        assertTrue(state.canManageMembers)
        assertTrue(state.canRemoveMembers)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(listOf("11", "10", "12"), state.members.map { it.id })
        assertEquals(listOf("방장", "같은 닉네임", "같은 닉네임"), state.members.map { it.nickname })
        assertEquals("https://cdn.example.com/member.png", state.members[1].profileImageUrl)
        assertEquals(listOf(true, false, false), state.members.map { it.isOwner })
        assertEquals(listOf("2026-09-16", "2026-09-17", "2026-09-18"), state.members.map { it.joinedDate })
    }

    @Test
    fun `같은 화면의 중복 진입은 조회를 합치고 재진입은 새 조회로 이전 요청을 무효화한다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val response = CompletableDeferred<List<RoomManagementMember>>()
        var requests = 0
        val viewModel = RoomMemberManagementViewModel(
            loadMembers = {
                requests += 1
                if (requests == 1) response.await() else listOf(member(20, "새 진입 회원"))
            },
            authSession = authSession,
            coroutineScope = this,
        )
        val firstRouteToken = Any()

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value, firstRouteToken)
        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value, firstRouteToken)
        runCurrent()
        assertEquals(1, requests)

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value, Any())
        runCurrent()
        response.complete(members())
        runCurrent()

        assertEquals(2, requests)
        assertEquals(listOf("새 진입 회원"), viewModel.uiState.value.members.map { it.nickname })
    }

    @Test
    fun `승인 전 취소와 방장 행 선택은 추방 요청을 보내지 않는다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        var kickRequests = 0
        val viewModel = RoomMemberManagementViewModel(
            loadMembers = { members() },
            kickMember = { _, _ -> kickRequests += 1 },
            authSession = authSession,
            coroutineScope = this,
        )
        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        viewModel.onRemoveMemberClick("11")
        assertNull(viewModel.uiState.value.selectedMemberId)
        viewModel.onRemoveMemberClick("10")
        assertEquals("10", viewModel.uiState.value.selectedMemberId)
        viewModel.onDismissRemove()
        viewModel.onConfirmRemove()
        runCurrent()

        assertEquals(0, kickRequests)
        assertNull(viewModel.uiState.value.selectedMemberId)
    }

    @Test
    fun `최종 승인에서만 선택한 userId를 한 번 추방하고 서버 목록을 다시 조회한다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val pendingKick = CompletableDeferred<Unit>()
        val serverMembers = members().toMutableList()
        val requests = mutableListOf<Pair<Long, Long>>()
        var loadRequests = 0
        val viewModel = RoomMemberManagementViewModel(
            loadMembers = {
                loadRequests += 1
                serverMembers.toList()
            },
            kickMember = { roomId, userId ->
                requests += roomId to userId
                pendingKick.await()
                serverMembers.removeAll { it.userId == userId }
            },
            authSession = authSession,
            coroutineScope = this,
        )
        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()
        viewModel.onRemoveMemberClick("10")

        viewModel.onConfirmRemove()
        viewModel.onConfirmRemove()
        viewModel.onRemoveMemberClick("12")
        runCurrent()

        assertEquals(listOf(7L to 10L), requests)
        assertTrue(viewModel.uiState.value.isRemoving)
        assertEquals(listOf("11", "10", "12"), viewModel.uiState.value.members.map { it.id })

        pendingKick.complete(Unit)
        runCurrent()

        assertEquals(listOf(7L to 10L), requests)
        assertEquals(listOf("11", "12"), viewModel.uiState.value.members.map { it.id })
        assertEquals(2, loadRequests)
        assertTrue(viewModel.uiState.value.canRemoveMembers)
        assertFalse(viewModel.uiState.value.isRemoving)
    }

    @Test
    fun `추방 성공 후 목록 갱신 실패는 완료된 회원을 복원하지 않고 GET 재시도만 한다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        var loads = 0
        var kicks = 0
        val viewModel = RoomMemberManagementViewModel(
            loadMembers = {
                loads += 1
                if (loads == 2) error("조회 실패") else members()
            },
            kickMember = { _, _ -> kicks += 1 },
            authSession = authSession,
            coroutineScope = this,
        )
        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()
        viewModel.onRemoveMemberClick("10")
        viewModel.onConfirmRemove()
        runCurrent()

        assertEquals(1, kicks)
        assertEquals(listOf("11", "12"), viewModel.uiState.value.members.map { it.id })
        assertFalse(viewModel.uiState.value.canRemoveMembers)
        assertTrue(viewModel.uiState.value.refreshErrorMessage.orEmpty().contains("추방"))

        viewModel.onRetry()
        runCurrent()

        assertEquals(1, kicks)
        assertEquals(3, loads)
        assertTrue(viewModel.uiState.value.canRemoveMembers)
    }

    @Test
    fun `연결이 끊긴 추방 결과는 POST를 반복하지 않고 GET으로 대상의 상태를 확인한다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val serverMembers = members().toMutableList()
        var loads = 0
        var kicks = 0
        val viewModel = RoomMemberManagementViewModel(
            loadMembers = {
                loads += 1
                serverMembers.toList()
            },
            kickMember = { _, userId ->
                kicks += 1
                serverMembers.removeAll { it.userId == userId }
                error("응답 연결이 끊김")
            },
            authSession = authSession,
            coroutineScope = this,
        )
        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()
        viewModel.onRemoveMemberClick("10")
        viewModel.onConfirmRemove()
        runCurrent()

        assertEquals(1, kicks)
        assertEquals(2, loads)
        assertEquals(listOf("11", "12"), viewModel.uiState.value.members.map { it.id })
        assertTrue(viewModel.uiState.value.removeErrorMessage.orEmpty().contains("확인할 수 없"))
        assertTrue(viewModel.uiState.value.canRemoveMembers)
    }

    @Test
    fun `회원관리 권한 오류는 목록과 추방 권한을 정리한다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val viewModel = RoomMemberManagementViewModel(
            loadMembers = { throw RoomRepositoryException(403, "MEMBER403", "권한 없음") },
            authSession = authSession,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", authSession.state.value, authSession.identity.value)
        runCurrent()

        assertFalse(viewModel.uiState.value.canManageMembers)
        assertFalse(viewModel.uiState.value.canRemoveMembers)
        assertEquals("방장만 회원을 관리할 수 있어요.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.canRetryLoad)
    }

    @Test
    fun `잘못된 모임 ID와 비로그인 상태에서는 서버 요청을 보내지 않는다`() = runTest {
        val authSession = AuthSession()
        var loads = 0
        val viewModel = RoomMemberManagementViewModel(
            loadMembers = {
                loads += 1
                members()
            },
            authSession = authSession,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("0", AuthSessionState.Authenticated, Any())
        viewModel.onRouteVisible("7", AuthSessionState.Unauthenticated, null)
        runCurrent()

        assertEquals(0, loads)
        assertFalse(viewModel.uiState.value.canManageMembers)
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("로그인이 필요해요"))
    }

    @Test
    fun `계정 변경 뒤 늦게 도착한 이전 조회는 새 계정의 목록을 덮어쓰지 않는다`() = runTest {
        val authSession = AuthSession().apply { startNewSession() }
        val oldResponse = CompletableDeferred<List<RoomManagementMember>>()
        var loads = 0
        val viewModel = RoomMemberManagementViewModel(
            loadMembers = {
                loads += 1
                if (loads == 1) oldResponse.await() else listOf(member(20, "새 계정 회원"))
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
        oldResponse.complete(listOf(member(30, "이전 계정 회원")))
        runCurrent()

        assertEquals(listOf("새 계정 회원"), viewModel.uiState.value.members.map { it.nickname })
    }

    private fun members() = listOf(
        member(11, "방장", RoomMembershipRole.OWNER, "/images/owner.png", "2026-09-16T08:00:00"),
        member(10, "같은 닉네임", image = "https://cdn.example.com/member.png", joinedAt = "2026-09-17T08:00:00"),
        member(12, "같은 닉네임", joinedAt = "2026-09-18T08:00:00"),
    )

    private fun member(
        userId: Long,
        nickname: String = "회원 $userId",
        membershipRole: RoomMembershipRole = RoomMembershipRole.MEMBER,
        image: String? = null,
        joinedAt: String = "2026-09-20T10:30:00",
    ) = RoomManagementMember(
        userId = userId,
        nickname = nickname,
        profileImageUrl = image,
        joinedAt = joinedAt,
        membershipRole = membershipRole,
    )
}
