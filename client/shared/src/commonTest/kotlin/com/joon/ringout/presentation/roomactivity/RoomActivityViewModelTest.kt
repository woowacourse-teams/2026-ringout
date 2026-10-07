package com.joon.ringout.presentation.roomactivity

import androidx.lifecycle.ViewModelStore
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.RoomMemberMovement
import com.joon.ringout.domain.room.RoomMemberMovementStatus
import com.joon.ringout.domain.room.RoomActivityRecord
import com.joon.ringout.domain.room.RoomMemberRecords
import com.joon.ringout.domain.room.RoomRecordEvent
import com.joon.ringout.domain.room.RoomRecords
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.presentation.roomhome.RoomHomeRecordEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RoomActivityViewModelTest {
    private val emptyRecordsLoader: suspend (Long, MissionDate) -> RoomRecords = { _, _ -> RoomRecords(emptyList()) }

    @Test
    fun `이동 상태 조회 결과를 서버 순서 그대로 회원 화면 모델로 표시한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val requests = mutableListOf<Long>()
        val viewModel = RoomActivityViewModel(
            loadMembers = { roomId ->
                requests += roomId
                listOf(
                    movement(3, "대기", RoomMemberMovementStatus.Idle),
                    movement(4, "준비", RoomMemberMovementStatus.AlarmTriggered),
                    movement(5, "시작", RoomMemberMovementStatus.MovementStarted),
                    movement(6, "이동", RoomMemberMovementStatus.Moving),
                    movement(7, "도착", RoomMemberMovementStatus.Arrived),
                    movement(8, "포기", RoomMemberMovementStatus.GaveUp),
                    movement(9, "알수없음", RoomMemberMovementStatus.Unknown),
                )
            },
            loadRecords = emptyRecordsLoader,
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf(7L), requests)
        assertEquals(
            listOf(
                RoomActivityMemberStatus.Waiting,
                RoomActivityMemberStatus.Preparing,
                RoomActivityMemberStatus.Moving,
                RoomActivityMemberStatus.Moving,
                RoomActivityMemberStatus.Arrived,
                RoomActivityMemberStatus.GaveUp,
                RoomActivityMemberStatus.Unknown,
            ),
            state.members.map { it.status },
        )
        assertTrue(state.isDataLoaded)
        assertFalse(state.isInitialLoading)
        assertNull(state.errorMessage)
        viewModel.onPause()
    }

    @Test
    fun `최초 조회 실패는 재시도 가능한 오류로 표시하고 회원 목록을 만들지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var calls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = {
                calls += 1
                error("network")
            },
            loadRecords = emptyRecordsLoader,
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.errorMessage.orEmpty().contains("불러오지 못했어요"))
        assertTrue(state.canRetry)
        assertFalse(state.isDataLoaded)
        assertTrue(state.members.isEmpty())
        advanceTimeBy(RoomActivityPollingIntervalMillis)
        runCurrent()
        assertEquals(1, calls)
        viewModel.onPause()
    }

    @Test
    fun `후속 갱신 실패는 기존 목록과 갱신 실패 메시지를 유지하고 자동 polling을 멈춘다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var calls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = {
                calls += 1
                if (calls == 1) listOf(movement(10, "러너", RoomMemberMovementStatus.Moving)) else error("refresh failed")
            },
            loadRecords = emptyRecordsLoader,
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        advanceTimeBy(RoomActivityPollingIntervalMillis)
        runCurrent()
        advanceTimeBy(RoomActivityPollingIntervalMillis)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(2, calls)
        assertEquals(listOf("러너"), state.members.map { it.nickname })
        assertTrue(state.refreshErrorMessage.orEmpty().contains("갱신하지 못했어요"))
        viewModel.onPause()
    }

    @Test
    fun `resume은 즉시 조회하고 10초마다 한 개의 polling 작업만 유지하며 pause에서 중단한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var calls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = {
                calls += 1
                listOf(movement(calls.toLong(), "회원$calls", RoomMemberMovementStatus.Idle))
            },
            loadRecords = emptyRecordsLoader,
            authSession = session,
            coroutineScope = this,
        )
        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        runCurrent()

        viewModel.onResume()
        viewModel.onResume()
        runCurrent()
        advanceTimeBy(RoomActivityPollingIntervalMillis)
        runCurrent()
        viewModel.onPause()
        advanceTimeBy(RoomActivityPollingIntervalMillis)
        runCurrent()

        assertEquals(2, calls)
        assertEquals(listOf("회원2"), viewModel.uiState.value.members.map { it.nickname })
    }

    @Test
    fun `계정과 활동일이 바뀐 뒤 늦은 응답은 현재 화면 상태를 덮어쓰지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val oldResponse = CompletableDeferred<List<RoomMemberMovement>>()
        var calls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = {
                calls += 1
                if (calls == 1) oldResponse.await() else listOf(movement(20, "새 화면 회원", RoomMemberMovementStatus.Arrived))
            },
            loadRecords = emptyRecordsLoader,
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        session.startNewSession()
        val nextDate = MissionDate.of(2026, 10, 8)
        viewModel.onAuthSessionChanged(session.state.value, session.identity.value)
        viewModel.onRouteVisible("7", nextDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        oldResponse.complete(listOf(movement(30, "이전 화면 회원", RoomMemberMovementStatus.GaveUp)))
        runCurrent()

        assertEquals(nextDate, viewModel.uiState.value.activityDate)
        assertEquals(listOf("새 화면 회원"), viewModel.uiState.value.members.map { it.nickname })
        viewModel.onPause()
    }

    @Test
    fun `권한 오류는 목록을 비우고 자동 재시도를 막는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val viewModel = RoomActivityViewModel(
            loadMembers = { throw RoomRepositoryException(500, "ROOM403", "권한 없음") },
            loadRecords = emptyRecordsLoader,
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.errorMessage.orEmpty().contains("권한"))
        assertFalse(state.canRetry)
        assertTrue(state.members.isEmpty())
    }

    @Test
    fun `세션 복원 중에는 polling을 시작하지 않고 서버 요청을 보내지 않는다`() = runTest {
        val session = AuthSession()
        var calls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = {
                calls += 1
                listOf(movement(1, "회원", RoomMemberMovementStatus.Idle))
            },
            loadRecords = emptyRecordsLoader,
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, AuthSessionState.Restoring, null)
        viewModel.onResume()
        runCurrent()
        advanceTimeBy(RoomActivityPollingIntervalMillis)
        runCurrent()

        assertEquals(0, calls)
        assertTrue(viewModel.uiState.value.isInitialLoading)
    }

    @Test
    fun `ViewModelStore가 정리되면 진행 중 조회와 polling을 취소한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val response = CompletableDeferred<List<RoomMemberMovement>>()
        val recordsResponse = CompletableDeferred<RoomRecords>()
        var calls = 0
        var cancelled = false
        var recordsCancelled = false
        val viewModel = RoomActivityViewModel(
            loadMembers = {
                calls += 1
                try {
                    response.await()
                } finally {
                    cancelled = true
                }
            },
            loadRecords = { _, _ ->
                try {
                    recordsResponse.await()
                } finally {
                    recordsCancelled = true
                }
            },
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        clearViewModel(viewModel)
        advanceTimeBy(RoomActivityPollingIntervalMillis)
        runCurrent()
        response.complete(listOf(movement(1, "늦은 회원", RoomMemberMovementStatus.Arrived)))
        runCurrent()

        assertEquals(1, calls)
        assertTrue(cancelled)
        assertTrue(recordsCancelled)
        assertTrue(viewModel.uiState.value.members.isEmpty())
        assertTrue(viewModel.uiState.value.timeline.isEmpty())
    }

    @Test
    fun `잘못된 모임 ID와 비로그인 상태에서는 서버 요청을 보내지 않는다`() = runTest {
        val session = AuthSession()
        var calls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = {
                calls += 1
                listOf(movement(1, "회원", RoomMemberMovementStatus.Idle))
            },
            loadRecords = emptyRecordsLoader,
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("0", activityDate.iso8601, AuthSessionState.Authenticated, Any())
        viewModel.onRouteVisible("7", activityDate.iso8601, AuthSessionState.Unauthenticated, null)
        viewModel.onResume()
        runCurrent()

        assertEquals(0, calls)
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("로그인이 필요해요"))
    }

    @Test
    fun `복원된 활동일 문자열이 잘못되면 예외 없이 서버 요청을 보내지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var calls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = {
                calls += 1
                listOf(movement(1, "회원", RoomMemberMovementStatus.Idle))
            },
            loadRecords = emptyRecordsLoader,
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", "2026-02-31", session.state.value, session.identity.value)
        runCurrent()

        assertEquals(0, calls)
        assertNull(viewModel.uiState.value.activityDate)
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("올바르지 않아요"))
    }

    @Test
    fun `경로 활동일이 전날이어도 기록 호출 직전의 KST 당일을 조회한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val today = MissionDate.of(2026, 10, 8)
        val requestedDates = mutableListOf<MissionDate>()
        val viewModel = RoomActivityViewModel(
            loadMembers = { emptyList() },
            loadRecords = { _, date ->
                requestedDates += date
                oneRecord(RoomRecordEvent.MOVEMENT_STARTED, "2026-10-08T00:02:00+09:00")
            },
            currentRecordsDate = { today },
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()

        assertEquals(listOf(today), requestedDates)
        assertEquals(activityDate, viewModel.uiState.value.activityDate)
        assertEquals(today, viewModel.uiState.value.timelineDate)
        assertEquals("00:02", viewModel.uiState.value.timeline.single().record.timeText)
        viewModel.onPause()
    }

    @Test
    fun `새 날짜를 조회하면 전날 타임라인을 비우고 새 날짜 기록을 요청한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var today = MissionDate.of(2026, 10, 7)
        val requestedDates = mutableListOf<MissionDate>()
        var calls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = { emptyList() },
            loadRecords = { _, date ->
                requestedDates += date
                calls += 1
                if (calls == 1) oneRecord(RoomRecordEvent.MOVEMENT_STARTED, "2026-10-07T23:58:00+09:00")
                else throw IllegalStateException("offline")
            },
            currentRecordsDate = { today },
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        assertEquals(1, viewModel.uiState.value.timeline.size)

        today = MissionDate.of(2026, 10, 8)
        viewModel.onRefresh()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(listOf(MissionDate.of(2026, 10, 7), today), requestedDates)
        assertEquals(today, state.timelineDate)
        assertTrue(state.timeline.isEmpty())
        assertFalse(state.isTimelineDataLoaded)
        assertTrue(state.timelineErrorMessage.orEmpty().contains("불러오지 못했어요"))
        viewModel.onPause()
    }

    @Test
    fun `회원 상태 조회가 실패해도 성공한 기록을 표시한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val viewModel = RoomActivityViewModel(
            loadMembers = { error("members offline") },
            loadRecords = { _, _ -> oneRecord(RoomRecordEvent.ARRIVED, "2026-10-07T21:00:00Z") },
            currentRecordsDate = { activityDate },
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.errorMessage.orEmpty().contains("회원 이동 상태"))
        assertEquals(RoomHomeRecordEvent.Arrived, state.timeline.single().record.event)
        assertTrue(state.isTimelineDataLoaded)
        viewModel.onPause()
    }

    @Test
    fun `기록 조회 실패에도 성공한 회원 상태를 표시하고 타임라인만 재시도한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var recordCalls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = { listOf(movement(18, "이동 회원", RoomMemberMovementStatus.Moving)) },
            loadRecords = { _, _ ->
                recordCalls += 1
                if (recordCalls == 1) error("records offline")
                oneRecord(RoomRecordEvent.GAVE_UP, "2026-10-07T21:00:00Z")
            },
            currentRecordsDate = { activityDate },
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        assertEquals("이동 회원", viewModel.uiState.value.members.single().nickname)
        assertTrue(viewModel.uiState.value.canRetryTimeline)
        assertTrue(viewModel.uiState.value.timeline.isEmpty())

        viewModel.onRetryTimeline()
        runCurrent()

        assertEquals(2, recordCalls)
        assertEquals("이동 회원", viewModel.uiState.value.members.single().nickname)
        assertEquals(RoomHomeRecordEvent.ForceEnded, viewModel.uiState.value.timeline.single().record.event)
        viewModel.onPause()
    }

    @Test
    fun `같은 날짜의 갱신 실패는 마지막 성공 타임라인을 유지하고 타임라인 자동 갱신만 멈춘다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var recordCalls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = { emptyList() },
            loadRecords = { _, _ ->
                recordCalls += 1
                if (recordCalls == 1) oneRecord(RoomRecordEvent.ARRIVED, "2026-10-07T21:00:00Z")
                else error("refresh offline")
            },
            currentRecordsDate = { activityDate },
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        val previousTimeline = viewModel.uiState.value.timeline
        advanceTimeBy(RoomActivityPollingIntervalMillis)
        runCurrent()
        advanceTimeBy(RoomActivityPollingIntervalMillis)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(2, recordCalls)
        assertEquals(previousTimeline, state.timeline)
        assertTrue(state.timelineRefreshErrorMessage.orEmpty().contains("갱신하지 못했어요"))
        viewModel.onPause()
    }

    @Test
    fun `polling 때마다 당일을 다시 계산해 다음 날짜 기록으로 전환한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var today = MissionDate.of(2026, 10, 7)
        val requestedDates = mutableListOf<MissionDate>()
        val viewModel = RoomActivityViewModel(
            loadMembers = { emptyList() },
            loadRecords = { _, date ->
                requestedDates += date
                oneRecord(RoomRecordEvent.MOVEMENT_STARTED, if (date == activityDate) "2026-10-07T23:59:00+09:00" else "2026-10-08T00:01:00+09:00")
            },
            currentRecordsDate = { today },
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        today = MissionDate.of(2026, 10, 8)
        advanceTimeBy(RoomActivityPollingIntervalMillis)
        runCurrent()

        assertEquals(listOf(activityDate, MissionDate.of(2026, 10, 8)), requestedDates)
        assertEquals(MissionDate.of(2026, 10, 8), viewModel.uiState.value.timelineDate)
        assertEquals("00:01", viewModel.uiState.value.timeline.single().record.timeText)
        viewModel.onPause()
    }

    @Test
    fun `자정 전에 시작한 기록 요청의 늦은 응답은 버리고 새 날짜를 다시 조회한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val oldResponse = CompletableDeferred<RoomRecords>()
        var today = MissionDate.of(2026, 10, 7)
        val requestedDates = mutableListOf<MissionDate>()
        val viewModel = RoomActivityViewModel(
            loadMembers = { emptyList() },
            loadRecords = { _, date ->
                requestedDates += date
                if (requestedDates.size == 1) oldResponse.await()
                else oneRecord(RoomRecordEvent.ARRIVED, "2026-10-08T00:01:00+09:00")
            },
            currentRecordsDate = { today },
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        today = MissionDate.of(2026, 10, 8)
        oldResponse.complete(oneRecord(RoomRecordEvent.GAVE_UP, "2026-10-07T23:59:59+09:00"))
        runCurrent()

        assertEquals(listOf(activityDate, MissionDate.of(2026, 10, 8)), requestedDates)
        assertEquals(RoomHomeRecordEvent.Arrived, viewModel.uiState.value.timeline.single().record.event)
        assertEquals(MissionDate.of(2026, 10, 8), viewModel.uiState.value.timelineDate)
        viewModel.onPause()
    }

    @Test
    fun `한쪽 API의 진행 중 요청은 polling 틱에서 중복 시작하지 않는다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val membersResponse = CompletableDeferred<List<RoomMemberMovement>>()
        val recordsResponse = CompletableDeferred<RoomRecords>()
        var memberCalls = 0
        var recordCalls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = {
                memberCalls += 1
                membersResponse.await()
            },
            loadRecords = { _, _ ->
                recordCalls += 1
                recordsResponse.await()
            },
            currentRecordsDate = { activityDate },
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        advanceTimeBy(RoomActivityPollingIntervalMillis * 2)
        runCurrent()

        assertEquals(1, memberCalls)
        assertEquals(1, recordCalls)
        membersResponse.complete(emptyList())
        recordsResponse.complete(RoomRecords(emptyList()))
        runCurrent()
        viewModel.onPause()
    }

    @Test
    fun `화면이 pause되면 회원 상태와 기록 요청을 모두 취소한다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        val membersResponse = CompletableDeferred<List<RoomMemberMovement>>()
        val recordsResponse = CompletableDeferred<RoomRecords>()
        var membersCancelled = false
        var recordsCancelled = false
        val viewModel = RoomActivityViewModel(
            loadMembers = {
                try {
                    membersResponse.await()
                } finally {
                    membersCancelled = true
                }
            },
            loadRecords = { _, _ ->
                try {
                    recordsResponse.await()
                } finally {
                    recordsCancelled = true
                }
            },
            currentRecordsDate = { activityDate },
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        viewModel.onPause()
        runCurrent()

        assertTrue(membersCancelled)
        assertTrue(recordsCancelled)
        assertTrue(viewModel.uiState.value.members.isEmpty())
        assertTrue(viewModel.uiState.value.timeline.isEmpty())
    }

    @Test
    fun `기록 권한 오류는 두 영역과 선택 회원을 모두 지운다`() = runTest {
        val session = AuthSession().apply { startNewSession() }
        var recordCalls = 0
        val viewModel = RoomActivityViewModel(
            loadMembers = { listOf(movement(18, "현재 회원", RoomMemberMovementStatus.Moving)) },
            loadRecords = { _, _ ->
                recordCalls += 1
                if (recordCalls == 1) oneRecord(RoomRecordEvent.MOVEMENT_STARTED, "2026-10-07T21:00:00Z")
                else throw RoomRepositoryException(403, "RECORD403", "권한 없음")
            },
            currentRecordsDate = { activityDate },
            authSession = session,
            coroutineScope = this,
        )

        viewModel.onRouteVisible("7", activityDate.iso8601, session.state.value, session.identity.value)
        viewModel.onResume()
        runCurrent()
        viewModel.onMembersClick(listOf("18"))
        assertEquals(listOf("18"), viewModel.uiState.value.selectedMemberIds)

        viewModel.onRefresh()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.members.isEmpty())
        assertTrue(state.timeline.isEmpty())
        assertNull(state.selectedMemberIds)
        assertFalse(state.canRetry)
        assertTrue(state.errorMessage.orEmpty().contains("권한"))
        assertFalse(state.showTimeline)
        viewModel.onPause()
    }

    private fun movement(
        userId: Long,
        nickname: String,
        status: RoomMemberMovementStatus,
    ) = RoomMemberMovement(userId, nickname, status)

    private fun oneRecord(event: RoomRecordEvent, occurredAt: String): RoomRecords = RoomRecords(
        listOf(
            RoomMemberRecords(
                userId = 42,
                nickname = "기록 회원",
                profileImageUrl = "https://example.test/profile.png",
                records = listOf(RoomActivityRecord(event, Instant.parse(occurredAt))),
            ),
        ),
    )

    private fun clearViewModel(viewModel: RoomActivityViewModel) {
        val store = ViewModelStore()
        store.put("room-activity", viewModel)
        store.clear()
    }

    private companion object {
        val activityDate = MissionDate.of(2026, 10, 7)
    }
}
