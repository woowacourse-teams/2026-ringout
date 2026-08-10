package com.joon.ringout.presentation.mypage

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MyPageStateTest {
    @Test
    fun august2026StartsOnSaturdayAndHas31Days() {
        val month = MyPageCalendarMonth(MissionYearMonth(2026, 8))
        val cells = buildCalendarCells(
            month = month,
            successDates = setOf(
                MissionDate.parse("2026-08-01"),
            ),
        )

        assertEquals(6, month.firstDayOfWeek)
        assertEquals(31, month.dayCount)
        assertEquals(1, cells[6].day)
        assertTrue(cells[6].isMissionSuccess)
        assertEquals(31, cells.count { it.day != null })
    }

    @Test
    fun leapYearFebruaryAndYearBoundariesAreCalculated() {
        assertEquals(29, MissionYearMonth(2024, 2).dayCount)
        assertEquals(MissionYearMonth(2025, 12), MissionYearMonth(2026, 1).previous())
        assertEquals(MissionYearMonth(2027, 1), MissionYearMonth(2026, 12).next())
    }

    @Test
    fun onlyDatesInSuccessSetAreMarkedForAStamp() {
        val cells = buildCalendarCells(
            month = MyPageCalendarMonth(MissionYearMonth(2026, 8)),
            successDates = setOf(
                MissionDate.parse("2026-08-03"),
            ),
        )

        assertTrue(cells.single { it.day == 3 }.isMissionSuccess)
        assertFalse(cells.single { it.day == 4 }.isMissionSuccess)
        assertFalse(cells.single { it.day == 5 }.isMissionSuccess)
    }

    @Test
    fun emptySuccessDatesLeaveEveryCalendarDayUnmarked() {
        val cells = buildCalendarCells(
            month = MyPageCalendarMonth(MissionYearMonth(2026, 8)),
            successDates = emptySet(),
        )

        assertTrue(cells.filter { it.day != null }.none { it.isMissionSuccess })
        assertFalse(cells.isEmpty())
    }

    @Test
    fun calendarGridAlwaysContainsSixCompleteWeeks() {
        val cells = buildCalendarCells(
            month = MyPageCalendarMonth(MissionYearMonth(2026, 2)),
            successDates = emptySet(),
        )

        assertEquals(CalendarCellCount, cells.size)
        assertEquals(CalendarWeekCount, cells.chunked(DaysPerWeek).size)
        assertEquals(28, cells.count { it.day != null })
        assertTrue(cells.drop(28).all { it.day == null })
    }

    @Test
    fun policyIdsMustBeUnique() {
        val duplicated = listOf(
            PolicyInfo(PolicyId("terms"), "이용약관", PolicyIcon.DOCUMENT),
            PolicyInfo(PolicyId("terms"), "추가 약관", PolicyIcon.DOCUMENT),
        )
        assertFails { requireUniquePolicyIds(duplicated) }
    }

    @Test
    fun defaultPoliciesPointToTheConfiguredHttpsPages() {
        assertEquals(
            "https://app.notion.com/p/3b858b8d8e6c80d2be23f7e4c0ff23bd?source=copy_link",
            findPolicyUrl(PolicyId("privacy")),
        )
        assertEquals(
            "https://sheer-mimosa-20f.notion.site/3b858b8d8e6c80de9724e0cd32000093?source=copy_link",
            findPolicyUrl(PolicyId("terms")),
        )
        assertNull(findPolicyUrl(PolicyId("unknown")))
        assertTrue(DefaultMyPagePolicies.all { findPolicyUrl(it.id) != null })
    }
}
