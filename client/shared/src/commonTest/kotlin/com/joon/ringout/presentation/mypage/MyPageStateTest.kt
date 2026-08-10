package com.joon.ringout.presentation.mypage

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MyPageStateTest {
    @Test
    fun august2026StartsOnSaturdayAndHas31Days() {
        val month = MyPageCalendarMonth(MissionYearMonth(2026, 8))
        val cells = buildCalendarCells(
            month = month,
            resultsByDate = mapOf(
                MissionDate.parse("2026-08-01") to MissionResult.SUCCESS,
            ),
        )

        assertEquals(6, month.firstDayOfWeek)
        assertEquals(31, month.dayCount)
        assertEquals(1, cells[6].day)
        assertEquals(MissionResult.SUCCESS, cells[6].result)
        assertEquals(31, cells.count { it.day != null })
    }

    @Test
    fun leapYearFebruaryAndYearBoundariesAreCalculated() {
        assertEquals(29, MissionYearMonth(2024, 2).dayCount)
        assertEquals(MissionYearMonth(2025, 12), MissionYearMonth(2026, 1).previous())
        assertEquals(MissionYearMonth(2027, 1), MissionYearMonth(2026, 12).next())
    }

    @Test
    fun successAndFailureResultsAreMappedToTheirCalendarDays() {
        val cells = buildCalendarCells(
            month = MyPageCalendarMonth(MissionYearMonth(2026, 8)),
            resultsByDate = mapOf(
                MissionDate.parse("2026-08-03") to MissionResult.SUCCESS,
                MissionDate.parse("2026-08-04") to MissionResult.FAILURE,
            ),
        )

        assertEquals(MissionResult.SUCCESS, cells.single { it.day == 3 }.result)
        assertEquals(MissionResult.FAILURE, cells.single { it.day == 4 }.result)
        assertEquals(null, cells.single { it.day == 5 }.result)
    }

    @Test
    fun emptyHistoryLeavesEveryCalendarDayWithoutAResult() {
        val cells = buildCalendarCells(
            month = MyPageCalendarMonth(MissionYearMonth(2026, 8)),
            resultsByDate = emptyMap(),
        )

        assertTrue(cells.filter { it.day != null }.all { it.result == null })
        assertFalse(cells.isEmpty())
    }

    @Test
    fun policyIdsMustBeUnique() {
        val duplicated = listOf(
            PolicyInfo(PolicyId("terms"), "이용약관", PolicyIcon.DOCUMENT),
            PolicyInfo(PolicyId("terms"), "추가 약관", PolicyIcon.DOCUMENT),
        )
        assertFails { requireUniquePolicyIds(duplicated) }
    }
}
