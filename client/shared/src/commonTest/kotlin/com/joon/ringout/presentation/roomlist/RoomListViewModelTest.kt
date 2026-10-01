package com.joon.ringout.presentation.roomlist

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.room.RoomCreateInput
import com.joon.ringout.domain.room.RoomMemberDetails
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.RoomSummary
import com.joon.ringout.presentation.roomlist.model.RoomMutationSource
import com.joon.ringout.presentation.roomlist.model.RoomMutationType
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import com.joon.ringout.presentation.roomlist.model.toRoomUiModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RoomListViewModelTest {
    @Test
    fun `인증 복구 중에는 목록 요청을 보류하고 복구 후 조회한다`() = runTest {
        var roomsCalls = 0
        val viewModel = RoomListViewModel(
            loadRooms = {
                roomsCalls += 1
                Result.success(listOf(previewRoom))
            },
            coroutineScope = this,
        )

        viewModel.onRouteVisible(AuthSessionState.Restoring)
        viewModel.onRetryRooms()
        runCurrent()

        assertEquals(0, roomsCalls)
        assertFalse(viewModel.uiState.isLoadingAllRooms)

        viewModel.onRouteVisible(AuthSessionState.Authenticated)
        runCurrent()

        assertEquals(1, roomsCalls)
        assertEquals(listOf(previewRoom), viewModel.uiState.allRooms)
    }

    @Test
    fun `전체 목록 한 번으로 참여 목록을 분리하고 전체 목록에도 같은 방을 유지한다`() = runTest {
        var roomsCalls = 0
        val notJoinedRoom = previewRoom.copy(id = "room-2", isJoined = false)
        val viewModel = RoomListViewModel(
            loadRooms = {
                roomsCalls += 1
                Result.success(listOf(previewRoom, notJoinedRoom))
            },
            coroutineScope = this,
        )

        viewModel.onRouteVisible(AuthSessionState.Authenticated)
        viewModel.onRouteVisible(AuthSessionState.Authenticated)
        runCurrent()

        assertEquals(1, roomsCalls)
        assertEquals(listOf(previewRoom, notJoinedRoom), viewModel.uiState.allRooms)
        assertEquals(listOf(previewRoom), viewModel.uiState.joinedRooms)
        assertTrue(viewModel.uiState.allRooms.containsAll(viewModel.uiState.joinedRooms))
        assertFalse(viewModel.uiState.isLoadingAllRooms)
    }

    @Test
    fun `로그아웃 후 이전 전체 목록 응답은 새 목록을 덮어쓰지 않는다`() = runTest {
        val lateResponse = CompletableDeferred<Result<List<RoomUiModel>>>()
        var roomsCalls = 0
        val notJoinedRoom = previewRoom.copy(id = "room-2", isJoined = false)
        val viewModel = RoomListViewModel(
            loadRooms = {
                roomsCalls += 1
                if (roomsCalls == 1) {
                    withContext(NonCancellable) { lateResponse.await() }
                } else {
                    Result.success(listOf(notJoinedRoom))
                }
            },
            coroutineScope = this,
        )

        viewModel.onRouteVisible(AuthSessionState.Authenticated)
        runCurrent()
        assertTrue(viewModel.uiState.isLoadingAllRooms)
        assertEquals(1, roomsCalls)

        viewModel.onRouteVisible(AuthSessionState.Unauthenticated)
        runCurrent()
        assertTrue(viewModel.uiState.joinedRooms.isEmpty())
        assertEquals(2, roomsCalls)
        assertEquals(listOf(notJoinedRoom), viewModel.uiState.allRooms)
        lateResponse.complete(Result.success(listOf(previewRoom.copy(id = "stale-room"))))
        runCurrent()

        assertTrue(viewModel.uiState.joinedRooms.isEmpty())
        assertFalse(viewModel.uiState.isAuthenticated)
        assertFalse(viewModel.uiState.isLoadingAllRooms)
        assertEquals(listOf(notJoinedRoom), viewModel.uiState.allRooms)
    }

    @Test
    fun `전체 목록 조회 오류는 재시도 후 목록 상태로 회복한다`() = runTest {
        var calls = 0
        val viewModel = RoomListViewModel(
            loadRooms = {
                calls += 1
                if (calls == 1) Result.failure(IllegalStateException("조회 실패"))
                else Result.success(listOf(previewRoom))
            },
            coroutineScope = this,
        )

        viewModel.onRouteVisible(AuthSessionState.Authenticated)
        runCurrent()

        assertEquals("모임 목록을 불러오는 중 문제가 발생했어요.", viewModel.uiState.allRoomsErrorMessage)
        assertFalse(viewModel.uiState.isLoadingAllRooms)

        viewModel.onRetryRooms()
        runCurrent()

        assertEquals(2, calls)
        assertEquals(listOf(previewRoom), viewModel.uiState.allRooms)
        assertEquals(listOf(previewRoom), viewModel.uiState.joinedRooms)
        assertEquals(null, viewModel.uiState.allRoomsErrorMessage)
    }

    @Test
    fun `모임 상세에서 로그인하면 인증 상태와 가입 여부를 다시 조회한다`() = runTest {
        var roomsCalls = 0
        val viewModel = RoomListViewModel(
            loadRooms = {
                roomsCalls += 1
                Result.success(listOf(previewRoom.copy(isJoined = roomsCalls > 1)))
            },
            coroutineScope = this,
        )

        viewModel.onRouteVisible(AuthSessionState.Unauthenticated)
        runCurrent()
        assertFalse(viewModel.uiState.allRooms.single().isJoined)

        viewModel.onRouteVisible(AuthSessionState.Authenticated)
        runCurrent()

        assertEquals(2, roomsCalls)
        assertTrue(viewModel.uiState.isAuthenticated)
        assertTrue(viewModel.uiState.allRooms.single().isJoined)
        assertEquals(viewModel.uiState.allRooms, viewModel.uiState.joinedRooms)
    }

    @Test
    fun `생성 성공은 새 모임을 즉시 목록에 반영하고 목록 갱신 오류에도 유지한다`() = runTest {
        var loadCalls = 0
        val existingRoom = previewRoom.copy(id = "room-2", isJoined = false)
        val viewModel = RoomListViewModel(
            loadRooms = {
                loadCalls += 1
                if (loadCalls == 1) Result.success(listOf(existingRoom))
                else Result.failure(IllegalStateException("refresh failed"))
            },
            createRoom = { Result.success(membershipDetails(roomId = 11L)) },
            coroutineScope = this,
        )
        val source = mutationSource(RoomMutationType.Create)
        viewModel.onRouteVisible(AuthSessionState.Authenticated, identity = Any())
        runCurrent()
        viewModel.onMutationSourceVisible(source.entryId)

        viewModel.createRoom(source, createInput())
        runCurrent()

        assertEquals(listOf("11", "room-2"), viewModel.uiState.allRooms.map(RoomUiModel::id))
        assertEquals(listOf("11"), viewModel.uiState.joinedRooms.map(RoomUiModel::id))
        assertEquals(RoomListRefreshErrorMessage, viewModel.uiState.allRoomsRefreshErrorMessage)
        assertTrue(viewModel.mutationState.isSuccessful)
        assertEquals("11", viewModel.mutationState.roomId)
    }

    @Test
    fun `생성 응답 뒤 시작한 목록 갱신은 오래된 GET 응답이 덮어쓰지 못한다`() = runTest {
        val lateRooms = CompletableDeferred<Result<List<RoomUiModel>>>()
        var loadCalls = 0
        val viewModel = RoomListViewModel(
            loadRooms = {
                loadCalls += 1
                if (loadCalls == 1) withContext(NonCancellable) { lateRooms.await() }
                else Result.success(listOf(membershipDetails(roomId = 12L).room.toRoomUiModel()))
            },
            createRoom = { Result.success(membershipDetails(roomId = 12L)) },
            coroutineScope = this,
        )
        val source = mutationSource(RoomMutationType.Create)
        viewModel.onRouteVisible(AuthSessionState.Authenticated, identity = Any())
        runCurrent()
        viewModel.onMutationSourceVisible(source.entryId)

        viewModel.createRoom(source, createInput())
        runCurrent()
        lateRooms.complete(Result.success(listOf(previewRoom.copy(id = "stale-room"))))
        runCurrent()

        assertEquals(2, loadCalls)
        assertEquals(listOf("12"), viewModel.uiState.allRooms.map(RoomUiModel::id))
    }

    @Test
    fun `로그인 계정이 바뀌면 진행 중 생성 결과와 모임 목록을 버린다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val pendingCreate = CompletableDeferred<Result<RoomMembershipDetails>>()
        val viewModel = RoomListViewModel(
            loadRooms = { Result.success(emptyList()) },
            createRoom = { withContext(NonCancellable) { pendingCreate.await() } },
            authSession = session,
            coroutineScope = this,
        )
        viewModel.onRouteVisible(session.state.value, session.identity.value)
        runCurrent()
        val source = mutationSource(RoomMutationType.Create)
        viewModel.onMutationSourceVisible(source.entryId)
        viewModel.createRoom(source, createInput())
        runCurrent()

        session.startNewSession()
        viewModel.onAuthSessionChanged(session.state.value, session.identity.value)
        runCurrent()
        pendingCreate.complete(Result.success(membershipDetails(roomId = 13L)))
        runCurrent()

        assertTrue(viewModel.uiState.allRooms.isEmpty())
        assertFalse(viewModel.mutationState.isSuccessful)
    }

    @Test
    fun `이미 가입한 응답이면 목록을 다시 받아 가입 상태를 확인한다`() = runTest {
        var loadCalls = 0
        val viewModel = RoomListViewModel(
            loadRooms = {
                loadCalls += 1
                val joined = loadCalls > 1
                Result.success(listOf(previewRoom.copy(id = "1", isJoined = joined)))
            },
            joinRoom = { Result.failure(RoomRepositoryException(409, "ROOM409", "이미 가입함")) },
            coroutineScope = this,
        )
        val source = mutationSource(RoomMutationType.Join, roomId = "1")
        viewModel.onRouteVisible(AuthSessionState.Authenticated, identity = Any())
        runCurrent()
        viewModel.onMutationSourceVisible(source.entryId)

        viewModel.joinRoom(source)
        runCurrent()

        assertEquals(2, loadCalls)
        assertEquals("1", viewModel.uiState.joinedRooms.single().id)
        assertEquals("이미 참여 중인 모임이에요. 목록의 가입 상태를 갱신했어요.", viewModel.mutationState.errorMessage)
        assertTrue(viewModel.mutationState.isMembershipConfirmed)
    }

    @Test
    fun `같은 화면에서 빠르게 가입을 눌러도 POST는 한 번만 실행한다`() = runTest {
        val pendingJoin = CompletableDeferred<Result<RoomMembershipDetails>>()
        var joinCalls = 0
        val viewModel = RoomListViewModel(
            loadRooms = { Result.success(emptyList()) },
            joinRoom = {
                joinCalls += 1
                pendingJoin.await()
            },
            coroutineScope = this,
        )
        val source = mutationSource(RoomMutationType.Join, roomId = "1")
        viewModel.onRouteVisible(AuthSessionState.Authenticated, identity = Any())
        runCurrent()
        viewModel.onMutationSourceVisible(source.entryId)

        viewModel.joinRoom(source)
        viewModel.joinRoom(source)
        runCurrent()

        assertEquals(1, joinCalls)
        pendingJoin.complete(Result.success(membershipDetails(roomId = 1L, role = RoomMembershipRole.MEMBER)))
        runCurrent()
        assertTrue(viewModel.mutationState.isSuccessful)
    }

    @Test
    fun `화면을 떠난 요청이 다시 열린 같은 화면을 자동 이동시키지 않는다`() = runTest {
        val pendingCreate = CompletableDeferred<Result<RoomMembershipDetails>>()
        val viewModel = RoomListViewModel(
            loadRooms = { Result.success(emptyList()) },
            createRoom = { withContext(NonCancellable) { pendingCreate.await() } },
            coroutineScope = this,
        )
        val firstSource = mutationSource(RoomMutationType.Create)
        val secondSource = mutationSource(RoomMutationType.Create).copy(entryId = 101L)
        viewModel.onRouteVisible(AuthSessionState.Authenticated, identity = Any())
        runCurrent()
        viewModel.onMutationSourceVisible(firstSource.entryId)
        viewModel.createRoom(firstSource, createInput())
        runCurrent()

        viewModel.onMutationSourceHidden(firstSource.entryId)
        viewModel.onMutationSourceVisible(secondSource.entryId)
        pendingCreate.complete(Result.success(membershipDetails(roomId = 14L)))
        runCurrent()

        val success = viewModel.consumeSuccessfulMutation(viewModel.mutationState.operationId)
        assertEquals(firstSource, success?.source)
        assertFalse(viewModel.isCurrentMutationSource(firstSource.entryId))
        assertTrue(viewModel.isCurrentMutationSource(secondSource.entryId))
    }

    private companion object {
        val previewRoom = RoomUiModel(
            id = "room-1",
            name = "퇴근 후 한강 러닝",
            description = "모임 소개 문구 미리보기입니다.",
            createdAt = "2026-09-15T09:00:00",
            activityDays = listOf("월", "수", "금"),
            activityTimeText = "오후 7:30",
            participantCount = 12,
            isJoined = true,
        )

        fun mutationSource(type: RoomMutationType, roomId: String? = null) = RoomMutationSource(
            entryId = 100L + (roomId?.toLongOrNull() ?: 0L),
            type = type,
            roomId = roomId,
        )

        fun createInput() = RoomCreateInput(
            name = "아침운동모임",
            description = "함께 운동해요",
            activityDays = listOf("MONDAY", "WEDNESDAY"),
            activityTime = "08:00",
        )

        fun membershipDetails(
            roomId: Long,
            role: RoomMembershipRole = RoomMembershipRole.OWNER,
        ) = RoomMembershipDetails(
            room = RoomSummary(
                id = roomId,
                name = "아침운동모임",
                description = "함께 운동해요",
                imageUrl = null,
                activityDays = listOf("MONDAY", "WEDNESDAY"),
                activityTime = "08:00",
                memberCount = 1,
                isJoined = true,
                createdAt = "2026-10-01T08:30:00",
            ),
            membershipRole = role,
            members = listOf(RoomMemberDetails(userId = 10L, nickname = "방장")),
        )
    }
}
