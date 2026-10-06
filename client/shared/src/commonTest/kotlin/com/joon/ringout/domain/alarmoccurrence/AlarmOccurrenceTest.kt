package com.joon.ringout.domain.alarmoccurrence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class AlarmOccurrenceTest {
    private val time = Instant.parse("2026-10-01T07:00:00+09:00")

    @Test
    fun `알람과 재울림 식별자가 비어 있거나 64자를 넘으면 거부한다`() {
        listOf("", "  ", "a".repeat(65)).forEach { id ->
            assertFailsWith<IllegalArgumentException> { AlarmOccurrenceStart(id, time, time) }
            assertFailsWith<IllegalArgumentException> { AlarmRepeatRinging(id, time) }
        }
        assertEquals(64, AlarmOccurrenceStart("a".repeat(64), time, time).alarmId.length)
        assertEquals(64, AlarmRepeatRinging("a".repeat(64), time).eventId.length)
    }

    @Test
    fun `서버 실행 식별자는 UUID만 허용한다`() {
        listOf("", "local-alarm-id", "../alarm", "00000000-0000-0000-0000-00000000000z").forEach { id ->
            assertFailsWith<IllegalArgumentException> { AlarmOccurrenceId(id) }
        }
        val id = "5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f"
        assertEquals(id, AlarmOccurrenceId(id).value)
    }
}
