package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.plusDays
import com.joon.ringout.domain.room.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RoomHomeRecordsLoadTest {
    @Test
    fun `기록 탭 첫 진입과 날짜 주 이동 새로고침만 조회하고 월 탐색은 조회하지 않는다`() = runTest {
        val requests = mutableListOf<Pair<Long, MissionDate>>()
        val session = AuthSession().apply { startNewSession() }
        val vm = model(session) { roomId, date -> requests += roomId to date; response() }
        vm.onRouteVisible("7", session.state.value, session.identity.value)
        runCurrent()
        assertTrue(requests.isEmpty())
        vm.onTabSelected(RoomHomeTab.Records)
        assertTrue(vm.uiState.value.recordsState.isLoading)
        vm.onDateSelected(day)
        assertTrue(vm.uiState.value.recordsState.isLoading)
        runCurrent()
        vm.onTabSelected(RoomHomeTab.Info)
        vm.onTabSelected(RoomHomeTab.Records)
        vm.onOpenCalendar()
        vm.onNextMonth()
        vm.onPreviousMonth()
        runCurrent()
        assertEquals(listOf(7L to day), requests)
        vm.onDateSelected(day.plusDays(1))
        runCurrent()
        vm.onNextWeek()
        runCurrent()
        vm.onPreviousWeek()
        runCurrent()
        vm.onRefresh()
        runCurrent()
        assertEquals(listOf(day, day.plusDays(1), day.plusDays(8), day.plusDays(1), day.plusDays(1)), requests.map { it.second })
        assertEquals(setOf(day, day.plusDays(1), day.plusDays(8)), vm.uiState.value.recordsState.participantCounts.keys)
    }

    @Test
    fun `날짜를 빠르게 바꾸면 취소를 무시한 이전 응답도 화면을 덮어쓰지 않는다`() = runTest {
        var old: Continuation<RoomRecords>? = null
        val session = AuthSession().apply { startNewSession() }
        val vm = model(session) { _, date ->
            if (date == day) suspendCoroutine { old = it } else RoomRecords(emptyList())
        }
        vm.enter(session)
        runCurrent()
        vm.onDateSelected(day.plusDays(1))
        runCurrent()
        old!!.resume(response())
        runCurrent()
        assertTrue(vm.uiState.value.recordsState.isDataLoaded)
        assertTrue(vm.uiState.value.recordsState.records.isEmpty())
        assertEquals(mapOf(day.plusDays(1) to 0), vm.uiState.value.recordsState.participantCounts)
    }

    @Test
    fun `일시적인 오류는 기록 영역만 비우고 선택 날짜에서 다시 조회한다`() = runTest {
        var fail = false
        val session = AuthSession().apply { startNewSession() }
        val vm = model(session) { _, _ ->
            if (fail) error("network") else response()
        }
        vm.enter(session)
        runCurrent()
        fail = true
        vm.onRefresh()
        runCurrent()
        assertNotNull(vm.uiState.value.room)
        assertNotNull(vm.uiState.value.recordsState.errorMessage)
        assertFalse(vm.uiState.value.recordsState.isDataLoaded)
        assertTrue(vm.uiState.value.recordsState.records.isEmpty())
        assertTrue(vm.uiState.value.recordsState.participantCounts.isEmpty())
        vm.onOpenCalendar()
        assertTrue(vm.uiState.value.isCalendarVisible)
        fail = false
        vm.onRefresh()
        runCurrent()
        assertTrue(vm.uiState.value.recordsState.isDataLoaded)
        assertNull(vm.uiState.value.recordsState.errorMessage)
    }

    @Test
    fun `권한 인증 삭제 오류는 다른 날짜에 조회한 기록과 프로필까지 모두 지운다`() = runTest {
        for (status in listOf(401, 403, 404)) {
            var fail = false
            val session = AuthSession().apply { startNewSession() }
            val vm = model(session) { _, _ ->
                if (fail) throw RoomRepositoryException(status, "RECORD$status", "실패")
                response()
            }
            vm.enter(session)
            runCurrent()
            fail = true
            vm.onDateSelected(day.plusDays(1))
            runCurrent()
            assertNull(vm.uiState.value.room)
            assertFalse(vm.uiState.value.canRetry)
            assertTrue(vm.uiState.value.recordsState.records.isEmpty())
            assertTrue(vm.uiState.value.recordsState.participantCounts.isEmpty())
            assertTrue(vm.uiState.value.recordsState.participantProfiles.isEmpty())
        }
    }

    @Test
    fun `방 변경과 계정 변경 및 로그아웃 후 이전 응답은 복원되지 않는다`() = runTest {
        for (change in listOf("room", "account", "logout")) {
            var old: Continuation<RoomRecords>? = null
            var count = 0
            val session = AuthSession().apply { startNewSession() }
            val vm = model(session) { _, _ ->
                if (++count == 1) suspendCoroutine { old = it } else RoomRecords(emptyList())
            }
            vm.enter(session)
            runCurrent()
            when (change) {
                "room" -> vm.onRouteVisible("8", session.state.value, session.identity.value)
                "account" -> {
                    session.startNewSession()
                    vm.onRouteVisible("7", session.state.value, session.identity.value)
                }
                else -> {
                    session.clear()
                    vm.onRouteVisible("7", session.state.value, session.identity.value)
                }
            }
            runCurrent()
            old!!.resume(response())
            runCurrent()
            assertTrue(vm.uiState.value.recordsState.records.isEmpty())
            assertFalse(vm.uiState.value.recordsState.participantCounts.containsValue(1))
            if (change == "logout") assertNull(vm.uiState.value.room)
        }
    }

    private fun TestScope.model(session: AuthSession, load: suspend (Long, MissionDate) -> RoomRecords) = RoomHomeViewModel(
        initialState = RoomHomeUiState(recordsState = RoomHomeRecordsUiState(selectedDate = day)),
        coroutineScope = this,
        authSession = session,
        loadRoom = { id -> RoomMembershipDetails(
            room = RoomSummary(id, "모임", "소개", null, listOf("MONDAY"), "08:00", 1, true, "2026-10-01T00:00:00"),
            membershipRole = RoomMembershipRole.MEMBER,
            members = emptyList(),
        ) },
        loadRecords = load,
    )

    private fun RoomHomeViewModel.enter(session: AuthSession) {
        onRouteVisible("7", session.state.value, session.identity.value)
        onTabSelected(RoomHomeTab.Records)
    }
}

private val day = MissionDate.parse("2026-10-01")
private fun response() = RoomRecords(listOf(RoomMemberRecords(1, "회원", "https://example.com/avatar", listOf(
    RoomActivityRecord(RoomRecordEvent.ARRIVED, Instant.parse("2026-10-01T09:00:00+09:00")),
))))
