package com.joon.ringout.domain.arrival

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrivalGrowthTest {
    @Test
    fun `성공한 날이 없으면 씨앗 다섯 칸이 비어 있다`() {
        val growth = ArrivalGrowth(successfulDayCount = 0)

        assertEquals(0, growth.filledSlots)
        assertEquals(5, growth.remainingDays)
        assertEquals(0, growth.completedTreeCount)
        assertFalse(growth.isTreeComplete)
    }

    @Test
    fun `첫 성공일에는 한 칸 자라고 네 번의 성공일이 남는다`() {
        val growth = ArrivalGrowth(successfulDayCount = 1)

        assertEquals(1, growth.filledSlots)
        assertEquals(4, growth.remainingDays)
        assertEquals(0, growth.completedTreeCount)
        assertFalse(growth.isTreeComplete)
    }

    @Test
    fun `네 번째 성공일에는 나무가 완성되기까지 하루가 남는다`() {
        val growth = ArrivalGrowth(successfulDayCount = 4)

        assertEquals(4, growth.filledSlots)
        assertEquals(1, growth.remainingDays)
        assertEquals(0, growth.completedTreeCount)
        assertFalse(growth.isTreeComplete)
    }

    @Test
    fun `다섯 번째 성공일에는 다섯 칸과 완성된 첫 나무를 표시한다`() {
        val growth = ArrivalGrowth(successfulDayCount = 5)

        assertEquals(5, growth.filledSlots)
        assertEquals(0, growth.remainingDays)
        assertEquals(1, growth.completedTreeCount)
        assertTrue(growth.isTreeComplete)
    }

    @Test
    fun `여섯 번째 성공일에는 완성된 나무를 유지하고 새 씨앗 한 칸을 채운다`() {
        val growth = ArrivalGrowth(successfulDayCount = 6)

        assertEquals(1, growth.filledSlots)
        assertEquals(4, growth.remainingDays)
        assertEquals(1, growth.completedTreeCount)
        assertFalse(growth.isTreeComplete)
    }

    @Test
    fun `열 번째 성공일에는 두 번째 나무가 완성된다`() {
        val growth = ArrivalGrowth(successfulDayCount = 10)

        assertEquals(5, growth.filledSlots)
        assertEquals(0, growth.remainingDays)
        assertEquals(2, growth.completedTreeCount)
        assertTrue(growth.isTreeComplete)
    }

    @Test
    fun `성공 날짜 수가 음수이면 성장을 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { ArrivalGrowth(successfulDayCount = -1) }
    }
}
