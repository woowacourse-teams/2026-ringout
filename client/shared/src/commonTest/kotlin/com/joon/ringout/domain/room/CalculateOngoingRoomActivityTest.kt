package com.joon.ringout.domain.room

import com.joon.ringout.domain.missionhistory.MissionDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculateOngoingRoomActivityTest {
    @Test
    fun `활동 시작 정각은 포함하고 종료 정각은 제외한다`() {
        val clock = FakeRoomScheduleClock(elapsedMillis = 6 * HourMillis)
        val schedule = mondayAtSix
        val calculate = CalculateOngoingRoomActivity()

        assertNull(calculate(schedule, clock.now().copy(epochMillis = 6 * HourMillis - 1), clock))
        assertEquals(
            6 * HourMillis,
            calculate(schedule, clock.now(), clock)?.startEpochMillis,
        )
        assertEquals(
            7 * HourMillis,
            calculate(schedule, clock.now().copy(epochMillis = 7 * HourMillis - 1), clock)?.endEpochMillis,
        )
        assertNull(calculate(schedule, clock.now().copy(epochMillis = 7 * HourMillis), clock))
    }

    @Test
    fun `전날 늦은 활동은 자정 뒤에도 시작일을 유지하고 한 시간 뒤 종료한다`() {
        val clock = FakeRoomScheduleClock(elapsedMillis = DayMillis + 30 * MinuteMillis - 1)
        val schedule = RoomActivitySchedule(setOf(RoomActivityDay.Monday), 23, 30)
        val calculate = CalculateOngoingRoomActivity()

        val ongoing = calculate(schedule, clock.now(), clock)
        assertEquals(MissionDate.parse("2026-09-28"), ongoing?.date)
        assertEquals(DayMillis + 30 * MinuteMillis, ongoing?.endEpochMillis)

        clock.elapsedMillis = DayMillis + 30 * MinuteMillis
        assertNull(calculate(schedule, clock.now(), clock))
    }

    @Test
    fun `월말과 연말을 넘어온 전날 활동도 시작 날짜 기준으로 찾는다`() {
        val clock = FakeRoomScheduleClock(
            anchor = MissionDate.parse("2027-01-01"),
            elapsedMillis = 15 * MinuteMillis,
        )
        val schedule = RoomActivitySchedule(setOf(RoomActivityDay.Thursday), 23, 30)

        val ongoing = CalculateOngoingRoomActivity()(schedule, clock.now(), clock)

        assertEquals(MissionDate.parse("2026-12-31"), ongoing?.date)
    }

    @Test
    fun `활동 요일이 아니거나 요일과 시각 변환이 불가능하면 진행 중으로 판정하지 않는다`() {
        val clock = object : RoomScheduleClock {
            override fun now() = RoomScheduleMoment(MissionDate.parse("2026-09-29"), 1_000L)
            override fun atLocalTime(date: MissionDate, hour: Int, minute: Int): Long? = null
        }
        val calculate = CalculateOngoingRoomActivity()

        assertNull(calculate(RoomActivitySchedule(emptySet(), 6, 0), clock.now(), clock))
        assertNull(calculate(RoomActivitySchedule(setOf(RoomActivityDay.Monday), 6, 0), clock.now(), clock))
        assertNull(calculate(RoomActivitySchedule(setOf(RoomActivityDay.Tuesday), 6, 0), clock.now(), clock))
    }

    private val mondayAtSix = RoomActivitySchedule(setOf(RoomActivityDay.Monday), 6, 0)

    private companion object {
        const val HourMillis = 3_600_000L
        const val MinuteMillis = 60_000L
        const val DayMillis = 86_400_000L
    }
}
