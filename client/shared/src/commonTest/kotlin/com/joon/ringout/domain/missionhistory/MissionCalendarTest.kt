package com.joon.ringout.domain.missionhistory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MissionCalendarTest {
    @Test
    fun `연도를 넘는 주에는 이전 연도 일요일부터 새해 토요일까지 표시한다`() {
        assertEquals(
            listOf("2025-12-28", "2025-12-29", "2025-12-30", "2025-12-31", "2026-01-01", "2026-01-02", "2026-01-03"),
            MissionDate.parse("2026-01-01").weekDates().map(MissionDate::iso8601),
        )
    }

    @Test
    fun `윤년의 마지막 주에는 이월 이십구일과 삼월 날짜가 함께 포함된다`() {
        assertEquals(
            listOf("2024-02-25", "2024-02-26", "2024-02-27", "2024-02-28", "2024-02-29", "2024-03-01", "2024-03-02"),
            MissionDate.parse("2024-02-29").weekDates().map(MissionDate::iso8601),
        )
    }

    @Test
    fun `평년 이월과 세기 경계에서 존재하는 날짜만 이동한다`() {
        assertEquals(MissionDate.parse("2025-03-01"), MissionDate.parse("2025-02-28").plusDays(1))
        assertEquals(MissionDate.parse("1900-02-28"), MissionDate.parse("1900-03-01").plusDays(-1))
        assertEquals(MissionDate.parse("2000-02-29"), MissionDate.parse("2000-03-01").plusDays(-1))
        assertEquals(MissionDate.parse("2025-12-31"), MissionDate.parse("2026-01-07").plusDays(-7))
    }

    @Test
    fun `일요일을 선택하면 해당 날짜부터 일주일을 구성한다`() {
        val sunday = MissionDate.parse("2026-09-27")

        assertEquals(0, sunday.dayOfWeekIndex)
        assertEquals(sunday, sunday.weekDates().first())
        assertEquals(MissionDate.parse("2026-10-03"), sunday.weekDates().last())
    }

    @Test
    fun `월 달력은 시작 요일만큼 비우고 마지막 주까지 완성한다`() {
        val dates = MissionYearMonth(2026, 8).calendarDates()

        assertEquals(42, dates.size)
        assertEquals(List(6) { null }, dates.take(6))
        assertEquals(MissionDate.parse("2026-08-01"), dates[6])
        assertEquals(MissionDate.parse("2026-08-31"), dates[36])
        assertNull(dates.last())
        assertEquals(31, dates.filterNotNull().size)
    }
}
