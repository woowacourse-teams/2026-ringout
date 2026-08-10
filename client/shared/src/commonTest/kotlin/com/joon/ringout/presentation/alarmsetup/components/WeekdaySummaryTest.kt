package com.joon.ringout.presentation.alarmsetup.components

import kotlin.test.Test
import kotlin.test.assertEquals

class WeekdaySummaryTest {
    @Test
    fun allDaysAreSummarizedAsEveryDay() {
        assertEquals(
            "매일",
            weekdaySummary(listOf("월", "화", "수", "목", "금", "토", "일")),
        )
    }

    @Test
    fun saturdayAndSundayAreSummarizedAsWeekend() {
        assertEquals("주말", weekdaySummary(listOf("토", "일")))
    }

    @Test
    fun mondayThroughFridayAreSummarizedAsWeekdays() {
        assertEquals("평일", weekdaySummary(listOf("월", "화", "수", "목", "금")))
    }

    @Test
    fun otherSelectionsAreDisplayedInWeekdayOrder() {
        assertEquals("월 화 목 금", weekdaySummary(listOf("금", "월", "목", "화")))
    }

    @Test
    fun noSelectionHasNoSummary() {
        assertEquals("", weekdaySummary(emptyList()))
    }
}
