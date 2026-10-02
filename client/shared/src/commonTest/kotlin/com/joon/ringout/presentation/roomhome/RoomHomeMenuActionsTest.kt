package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.room.RoomMemberDetails
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.RoomSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RoomHomeMenuActionsTest {
    @Test
    fun `방장 외 회원이 있으면 삭제를 막고 회원 관리 안내를 연다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var deleteCalls = 0
        var loadCalls = 0
        val viewModel = RoomHomeViewModel(
            coroutineScope = this,
            loadRoom = { roomId ->
                loadCalls += 1
                details(roomId, RoomMembershipRole.OWNER, memberCount = 2)
            },
            deleteRoom = { deleteCalls += 1 },
            authSession = session,
        )
        viewModel.onRouteVisible("7", session.state.value, session.identity.value)
        runCurrent()

        viewModel.beginDelete()
        runCurrent()

        val state = assertIs<RoomHomeMenuActionState.Phase>(viewModel.uiState.value.menuActionState)
        assertEquals(RoomHomeActionPhase.DeleteBlockedByMembers, state.value)
        assertEquals(2, loadCalls)
        assertEquals(0, deleteCalls)
    }

    @Test
    fun `삭제 승인 직전 회원 수가 늘면 삭제 요청을 보내지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var loadCalls = 0
        var deleteCalls = 0
        val viewModel = RoomHomeViewModel(
            coroutineScope = this,
            loadRoom = { roomId ->
                loadCalls += 1
                val count = if (loadCalls >= 3) 2 else 1
                details(roomId, RoomMembershipRole.OWNER, count)
            },
            deleteRoom = { deleteCalls += 1 },
            authSession = session,
        )
        viewModel.onRouteVisible("7", session.state.value, session.identity.value)
        runCurrent()

        viewModel.beginDelete()
        runCurrent()
        assertEquals(
            RoomHomeActionPhase.ConfirmDelete,
            (viewModel.uiState.value.menuActionState as RoomHomeMenuActionState.Phase).value,
        )
        viewModel.confirmMenuAction()
        runCurrent()

        assertEquals(
            RoomHomeActionPhase.DeleteBlockedByMembers,
            (viewModel.uiState.value.menuActionState as RoomHomeMenuActionState.Phase).value,
        )
        assertEquals(3, loadCalls)
        assertEquals(0, deleteCalls)
    }

    @Test
    fun `삭제는 재확인 뒤 한 번만 요청하고 완료 이벤트도 한 번만 소비한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var deleteCalls = 0
        val viewModel = RoomHomeViewModel(
            coroutineScope = this,
            loadRoom = { roomId -> details(roomId, RoomMembershipRole.OWNER, memberCount = 1) },
            deleteRoom = { deleteCalls += 1 },
            authSession = session,
        )
        val identity = session.identity.value
        viewModel.onRouteVisible("7", session.state.value, identity)
        runCurrent()

        viewModel.beginDelete()
        runCurrent()
        viewModel.confirmMenuAction()
        viewModel.confirmMenuAction()
        runCurrent()

        assertEquals(1, deleteCalls)
        val completed = assertIs<RoomHomeMenuActionState.Completed>(viewModel.uiState.value.menuActionState)
        assertEquals(RoomHomeActionType.Delete, completed.actionType)
        assertEquals(7L, viewModel.consumeMenuActionCompletion(completed.operationId)?.roomId)
        assertNull(viewModel.consumeMenuActionCompletion(completed.operationId))
    }

    @Test
    fun `탈퇴 취소는 조회나 API를 호출하지 않고 승인하면 MEMBER만 탈퇴한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var loadCalls = 0
        var leaveCalls = 0
        val viewModel = RoomHomeViewModel(
            coroutineScope = this,
            loadRoom = { roomId ->
                loadCalls += 1
                details(roomId, RoomMembershipRole.MEMBER, memberCount = 2)
            },
            leaveRoom = { leaveCalls += 1 },
            authSession = session,
        )
        viewModel.onRouteVisible("7", session.state.value, session.identity.value)
        runCurrent()

        viewModel.beginLeave()
        viewModel.cancelMenuAction()
        assertNull(viewModel.uiState.value.menuActionState)
        assertEquals(1, loadCalls)
        assertEquals(0, leaveCalls)

        viewModel.beginLeave()
        viewModel.confirmMenuAction()
        runCurrent()

        assertEquals(2, loadCalls)
        assertEquals(1, leaveCalls)
        val completed = assertIs<RoomHomeMenuActionState.Completed>(viewModel.uiState.value.menuActionState)
        assertEquals(RoomHomeActionType.Leave, completed.actionType)
    }

    @Test
    fun `계정 변경 뒤 늦게 완료된 회원 확인은 삭제 동작을 시작하지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val pendingDetails = CompletableDeferred<RoomMembershipDetails>()
        var loadCalls = 0
        var deleteCalls = 0
        val viewModel = RoomHomeViewModel(
            coroutineScope = this,
            loadRoom = { roomId ->
                loadCalls += 1
                if (loadCalls == 2) withContext(NonCancellable) { pendingDetails.await() }
                else details(roomId, RoomMembershipRole.OWNER, memberCount = 1)
            },
            deleteRoom = { deleteCalls += 1 },
            authSession = session,
        )
        viewModel.onRouteVisible("7", session.state.value, session.identity.value)
        runCurrent()

        viewModel.beginDelete()
        runCurrent()
        session.startNewSession()
        viewModel.onAuthSessionChanged(session.state.value, session.identity.value)
        viewModel.onRouteVisible("7", session.state.value, session.identity.value)
        runCurrent()
        pendingDetails.complete(details(7L, RoomMembershipRole.OWNER, memberCount = 1))
        runCurrent()

        assertEquals(3, loadCalls)
        assertNull(viewModel.uiState.value.menuActionState)
        assertEquals(0, deleteCalls)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `탈퇴 중 서버에서 참여 상태가 바뀐 응답은 성공이나 홈 이동으로 취급하지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var calls = 0
        val viewModel = RoomHomeViewModel(
            coroutineScope = this,
            loadRoom = { roomId ->
                calls += 1
                details(roomId, RoomMembershipRole.MEMBER, memberCount = 1)
            },
            leaveRoom = { throw RoomRepositoryException(409, "ROOM409", "미참여") },
            authSession = session,
        )
        viewModel.onRouteVisible("7", session.state.value, session.identity.value)
        runCurrent()
        viewModel.beginLeave()
        viewModel.confirmMenuAction()
        runCurrent()

        val error = assertIs<RoomHomeMenuActionState.Error>(viewModel.uiState.value.menuActionState)
        assertTrue(error.isMembershipChanged)
        assertFalse(error.canRetry)
        assertNull(viewModel.consumeMenuActionCompletion(error.operationId))
        assertEquals(3, calls) // initial detail, pre-leave check, and membership-change refresh
    }
}

private fun details(
    roomId: Long,
    role: RoomMembershipRole,
    memberCount: Int,
): RoomMembershipDetails = RoomMembershipDetails(
    room = RoomSummary(
        id = roomId,
        name = "테스트 모임",
        description = "모임 설명",
        imageUrl = null,
        activityDays = listOf("MONDAY"),
        activityTime = "08:00",
        memberCount = memberCount,
        isJoined = true,
        createdAt = "2026-10-01T08:30:00",
    ),
    membershipRole = role,
    members = (1..memberCount).map { index ->
        RoomMemberDetails(userId = index.toLong(), nickname = "회원 $index")
    },
)
