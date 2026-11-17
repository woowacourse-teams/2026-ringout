package com.joon.ringout.domain.arrival

import com.joon.ringout.domain.missionhistory.MissionDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrivalResultTest {
    @Test
    fun `이후 성공일이 늘어도 이전 결과는 도착 당시 완성된 나무를 유지한다`() {
        val previous = result(successfulDays = 5)
        val later = result(successfulDays = 6).copy(
            occurrenceId = "later-arrival",
            completedLocalDate = MissionDate.parse("2026-11-15"),
        )

        assertEquals(5, previous.successfulDayCountAtArrival)
        assertEquals(5, previous.growth.filledSlots)
        assertTrue(previous.growth.isTreeComplete)
        assertEquals(1, later.growth.filledSlots)
        assertFalse(later.growth.isTreeComplete)
    }

    @Test
    fun `도착 회차 식별자가 비어 있으면 결과를 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { result().copy(occurrenceId = " ") }
    }

    @Test
    fun `성공 날짜가 없는 도착 결과는 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { result(successfulDays = 0) }
        assertFailsWith<IllegalArgumentException> { result(successfulDays = -1) }
    }

    private fun result(successfulDays: Int = 1) = ArrivalResult(
        occurrenceId = "arrival",
        completedLocalDate = MissionDate.parse("2026-11-09"),
        completedAtEpochMillis = 1_000,
        alarmDismissedAtEpochMillis = 500,
        successfulDayCountAtArrival = successfulDays,
        grewOnThisArrival = true,
        acknowledgedAtEpochMillis = null,
    )
}
