package com.joon.ringout.domain.room

import com.joon.ringout.domain.missionhistory.MissionDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculateNextRoomScheduleTest {
    private val calculate = CalculateNextRoomSchedule()
    private val daily = RoomActivitySchedule(RoomActivityDay.entries.toSet(), 6, 0)

    @Test
    fun `오늘 활동 시간이 남아 있으면 초 단위로 남은 시간을 계산한다`() {
        val clock = FakeRoomScheduleClock(elapsedMillis = 5 * 3_600_000L + 41 * 60_000L + 36_000)
        val result = calculate(daily, clock.now(), clock)!!
        assertEquals(MissionDate.parse("2026-09-28"), result.date)
        assertEquals(1_104L, result.remainingSeconds)
    }

    @Test
    fun `시작 직전 소수 초는 올림하고 정각에는 다음 활동일로 전환한다`() {
        val clock = FakeRoomScheduleClock(elapsedMillis = 6 * 3_600_000L - 1)
        assertEquals(1L, calculate(daily, clock.now(), clock)!!.remainingSeconds)
        clock.elapsedMillis++
        val result = calculate(daily, clock.now(), clock)!!
        assertEquals(MissionDate.parse("2026-09-29"), result.date)
        assertEquals(86_400L, result.remainingSeconds)
    }

    @Test
    fun `지나간 활동 요일은 다음 주 같은 요일까지 건너뛴다`() {
        val clock = FakeRoomScheduleClock(elapsedMillis = 6 * 3_600_000L)
        val result = calculate(daily.copy(days = setOf(RoomActivityDay.Monday)), clock.now(), clock)!!
        assertEquals(MissionDate.parse("2026-10-05"), result.date)
        assertEquals(7 * 86_400L, result.remainingSeconds)
    }

    @Test
    fun `여러 활동 요일 중 가장 가까운 요일을 선택한다`() {
        val clock = FakeRoomScheduleClock(elapsedMillis = 7 * 3_600_000L)
        val schedule = daily.copy(days = setOf(RoomActivityDay.Friday, RoomActivityDay.Wednesday))
        assertEquals(MissionDate.parse("2026-09-30"), calculate(schedule, clock.now(), clock)!!.date)
    }

    @Test
    fun `연말과 윤년의 날짜 경계를 넘어서 다음 일정을 계산한다`() {
        listOf("2026-12-31" to "2027-01-01", "2028-02-28" to "2028-02-29").forEach { (start, next) ->
            val clock = FakeRoomScheduleClock(MissionDate.parse(start), elapsedMillis = 23 * 3_600_000L)
            assertEquals(MissionDate.parse(next), calculate(daily, clock.now(), clock)!!.date)
        }
    }

    @Test
    fun `활동 요일이 없으면 다음 일정도 없다`() {
        val clock = FakeRoomScheduleClock()
        assertNull(calculate(daily.copy(days = emptySet()), clock.now(), clock))
    }

    @Test
    fun `남은 시간은 고정된 하루 길이 대신 시간대가 반영된 실제 시각 차이를 사용한다`() {
        val clock = FakeRoomScheduleClock(targetOffsetMillis = -3_600_000L)
        assertEquals(5 * 3_600L, calculate(daily, clock.now(), clock)!!.remainingSeconds)
    }
}
