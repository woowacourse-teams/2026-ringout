package com.joon.ringout.presentation.common

import kotlin.test.Test
import kotlin.test.assertEquals

class WeekdaySummaryTest {
    @Test
    fun `모든 요일은 매일로 요약한다`() {
        assertEquals(
            "매일",
            weekdaySummary(listOf("월", "화", "수", "목", "금", "토", "일")),
        )
    }

    @Test
    fun `토요일과 일요일은 주말로 요약한다`() {
        assertEquals("주말", weekdaySummary(listOf("토", "일")))
    }

    @Test
    fun `월요일부터 금요일은 평일로 요약한다`() {
        assertEquals("평일", weekdaySummary(listOf("월", "화", "수", "목", "금")))
    }

    @Test
    fun `그 외 선택은 요일 순서대로 표시한다`() {
        assertEquals("월 화 목 금", weekdaySummary(listOf("금", "월", "목", "화")))
    }

    @Test
    fun `선택한 요일이 없으면 빈 요약을 반환한다`() {
        assertEquals("", weekdaySummary(emptyList()))
    }
}
