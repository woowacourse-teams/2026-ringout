package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.plusDays
import com.joon.ringout.domain.missionhistory.weekDates
import com.joon.ringout.domain.room.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun `주간 최초 조회 후 날짜와 탭 변경 및 조회한 주로 복귀할 때 캐시만 표시한다`() = runTest {
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
        assertEquals(7, requests.size)
        assertEquals(day.weekDates().toSet(), requests.map { it.second }.toSet())
        assertTrue(requests.all { it.first == 7L })
        vm.onDateSelected(day.plusDays(1))
        runCurrent()
        assertEquals(7, requests.size)
        vm.onNextWeek()
        runCurrent()
        assertEquals(14, requests.size)
        vm.onPreviousWeek()
        runCurrent()
        assertEquals(14, requests.size)
        vm.onRefresh()
        runCurrent()
        assertEquals(21, requests.size)
        assertEquals(day.weekDates().toSet(), requests.takeLast(7).map { it.second }.toSet())
        assertEquals((day.weekDates() + day.plusDays(7).weekDates()).toSet(), vm.uiState.value.recordsState.participantCounts.keys)
    }

    @Test
    fun `조회 중 같은 주의 날짜를 바꾸면 이전 날짜 응답은 캐시에만 반영된다`() = runTest {
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
        assertEquals(day.weekDates().associateWith { if (it == day) 1 else 0 }, vm.uiState.value.recordsState.participantCounts)
        vm.onDateSelected(day)
        assertEquals(1, vm.uiState.value.recordsState.achievedMemberCount)
    }

    @Test
    fun `새로고침 실패는 오류를 표시하고 다시 새로고침하면 현재 주를 복구한다`() = runTest {
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
            vm.onDateSelected(day.plusDays(7))
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

    @Test
    fun `일부 날짜 실패는 빈 기록으로 캐시하지 않고 날짜 선택이나 탭 변경으로 재요청하지 않는다`() = runTest {
        var calls = 0
        var fail = true
        val failedDate = day.plusDays(1)
        val session = AuthSession().apply { startNewSession() }
        val vm = model(session) { _, date ->
            calls++
            if (fail && date == failedDate) error("network")
            RoomRecords(emptyList())
        }
        vm.enter(session)
        runCurrent()
        assertEquals(7, calls)
        assertEquals(6, vm.uiState.value.recordsState.participantCounts.size)
        assertFalse(failedDate in vm.uiState.value.recordsState.participantCounts)
        vm.onDateSelected(failedDate)
        assertNotNull(vm.uiState.value.recordsState.errorMessage)
        assertFalse(vm.uiState.value.recordsState.isDataLoaded)
        vm.onTabSelected(RoomHomeTab.Info)
        vm.onTabSelected(RoomHomeTab.Records)
        vm.onDateSelected(day)
        vm.onDateSelected(failedDate)
        runCurrent()
        assertEquals(7, calls)
        fail = false
        vm.onRefresh()
        runCurrent()
        assertEquals(14, calls)
        assertEquals(7, vm.uiState.value.recordsState.participantCounts.size)
        assertNull(vm.uiState.value.recordsState.errorMessage)
        assertTrue(vm.uiState.value.recordsState.isDataLoaded)
    }

    @Test
    fun `서로 다른 주를 조회해도 동시 요청은 세 개 이하이며 선택 날짜부터 조회한다`() = runTest {
        var active = 0
        var maximum = 0
        val requests = mutableListOf<MissionDate>()
        val session = AuthSession().apply { startNewSession() }
        val vm = model(session) { _, date ->
            requests += date
            active++
            maximum = maxOf(maximum, active)
            try {
                delay(1_000)
                RoomRecords(emptyList())
            } finally {
                active--
            }
        }
        vm.enter(session)
        runCurrent()
        assertEquals(day, requests.first())
        assertEquals(3, requests.size)
        vm.onNextWeek()
        runCurrent()
        assertTrue(vm.uiState.value.recordsState.isLoading)
        advanceUntilIdle()
        assertEquals(3, maximum)
        assertEquals(14, requests.size)
        assertEquals(day.plusDays(7), vm.uiState.value.recordsState.selectedDate)
        assertEquals(14, vm.uiState.value.recordsState.participantCounts.size)
        vm.onPreviousWeek()
        runCurrent()
        assertEquals(14, requests.size)
        assertTrue(vm.uiState.value.recordsState.isDataLoaded)
    }

    @Test
    fun `새로고침 이전의 늦은 주간 응답은 새 캐시를 덮어쓰지 않는다`() = runTest {
        var old: Continuation<RoomRecords>? = null
        var first = true
        val session = AuthSession().apply { startNewSession() }
        val vm = model(session) { _, date ->
            if (first && date == day) {
                first = false
                suspendCoroutine { old = it }
            } else RoomRecords(emptyList())
        }
        vm.enter(session)
        runCurrent()
        vm.onRefresh()
        runCurrent()
        assertTrue(vm.uiState.value.recordsState.isDataLoaded)
        old!!.resume(response())
        runCurrent()
        assertTrue(vm.uiState.value.recordsState.records.isEmpty())
        assertEquals(day.weekDates().associateWith { 0 }, vm.uiState.value.recordsState.participantCounts)
    }

    @Test
    fun `연도 경계의 주도 일곱 날짜만 조회하고 빈 기록을 캐시한다`() = runTest {
        val requests = mutableListOf<MissionDate>()
        val session = AuthSession().apply { startNewSession() }
        val vm = model(session) { _, date -> requests += date; RoomRecords(emptyList()) }
        vm.onDateSelected(MissionDate.parse("2026-12-31"))
        vm.enter(session)
        runCurrent()
        assertEquals((0..6).map { MissionDate.parse("2026-12-27").plusDays(it) }.toSet(), requests.toSet())
        vm.onDateSelected(MissionDate.parse("2027-01-01"))
        runCurrent()
        assertEquals(7, requests.size)
        assertTrue(vm.uiState.value.recordsState.isDataLoaded)
        assertTrue(vm.uiState.value.recordsState.records.isEmpty())
        assertEquals(0, vm.uiState.value.recordsState.achievedMemberCount)
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
