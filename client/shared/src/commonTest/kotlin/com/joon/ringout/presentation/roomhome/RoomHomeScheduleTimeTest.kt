package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.FakeRoomScheduleClock
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomHomeScheduleTimeTest {
    @Test
    fun `오전 오후 열두 시와 이십사 시간제 시간을 정확히 변환한다`() {
        mapOf("오전 12:00" to 0, "오후 12:00" to 12, "오후 01:30" to 13, "오전 6:00" to 6, "23:59" to 23)
            .forEach { (text, hour) -> assertEquals(hour, room.copy(activityTimeText = text).toActivitySchedule()!!.hour) }
    }

    @Test
    fun `잘못된 활동 시간이나 요일이면 고정 샘플 대신 일정 없음으로 표시한다`() {
        listOf("오전 00:00", "오후 13:00", "24:00", "06:60", "잘못된 시간").forEach { text ->
            val result = RoomHomeUiState(room = room.copy(activityTimeText = text), remainingTimeText = "샘플")
                .withCurrentSchedule(FakeRoomScheduleClock())
            assertNull(result.nextScheduleText)
            assertNull(result.remainingTimeText)
        }
        assertNull(room.copy(activityDays = listOf("알 수 없음")).toActivitySchedule())
    }

    @Test
    fun `하루 미만은 시분초로 하루 이상은 일과 시간으로 표시한다`() {
        mapOf(0L to "00:00:00", 1L to "00:00:01", 1_104L to "00:18:24", 86_399L to "23:59:59",
            86_400L to "1일 0시간", 129_600L to "1일 12시간").forEach { (seconds, text) ->
            assertEquals(text, formatRoomRemainingTime(seconds))
        }
    }

    @Test
    fun `다음 일정은 오늘 내일과 연도가 바뀌는 날짜를 구분한다`() {
        val clock = FakeRoomScheduleClock()
        val state = RoomHomeUiState(room = room)
        assertEquals("오늘 오전 06:00", state.withCurrentSchedule(clock).nextScheduleText)
        clock.elapsedMillis = 7 * 3_600_000L
        assertEquals("내일 오전 06:00", state.withCurrentSchedule(clock).nextScheduleText)
        val yearEndClock = FakeRoomScheduleClock(MissionDate.parse("2026-12-31"), 7 * 3_600_000L)
        val mondayRoom = room.copy(activityDays = listOf("월"))
        assertEquals("2027년 1월 4일 오전 06:00", state.copy(room = mondayRoom).withCurrentSchedule(yearEndClock).nextScheduleText)
    }

    @Test
    fun `정상 상세 상태에서만 진행 중 활동을 표시하고 시작일을 보존한다`() {
        val clock = FakeRoomScheduleClock(elapsedMillis = 6 * 3_600_000L + 30 * 60_000L)
        val loadedState = RoomHomeUiState(
            room = room,
            membershipRole = RoomMembershipRole.MEMBER,
            areMembersLoaded = true,
        )

        assertEquals(MissionDate.parse("2026-09-28"), loadedState.withCurrentSchedule(clock).ongoingActivity?.date)
        assertEquals(room.participantCount, loadedState.withCurrentSchedule(clock).ongoingActivity?.participantCount)
        assertNull(loadedState.copy(areMembersLoaded = false).withCurrentSchedule(clock).ongoingActivity)
        assertNull(loadedState.copy(isLoading = true).withCurrentSchedule(clock).ongoingActivity)
        assertNull(loadedState.copy(room = room.copy(isJoined = false)).withCurrentSchedule(clock).ongoingActivity)
        assertNull(loadedState.copy(errorMessage = "오류").withCurrentSchedule(clock).ongoingActivity)
        assertNull(loadedState.copy(room = room.copy(activityDays = emptyList())).withCurrentSchedule(clock).ongoingActivity)
        assertNull(loadedState.copy(room = room.copy(activityDays = listOf("오류"))).withCurrentSchedule(clock).ongoingActivity)
    }

    private val room = RoomUiModel(
        id = "schedule-test", name = "아침 모임", description = "함께 달리는 모임", createdAt = "2026-09-01",
        activityDays = listOf("월", "화", "수", "목", "금", "토", "일"), activityTimeText = "오전 06:00",
        participantCount = 2, isJoined = true,
    )
}
