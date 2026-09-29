package com.joon.ringout.presentation.roomlist

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
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

        assertEquals("조회 실패", viewModel.uiState.allRoomsErrorMessage)
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
    }
}
