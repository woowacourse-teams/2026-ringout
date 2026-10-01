package com.joon.ringout.domain.room

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RoomCreateInputTest {
    @Test
    fun `모임 소개는 UTF-16 기준 300자까지 허용하고 초과 입력은 거절한다`() {
        createInput(description = "가".repeat(300))
        createInput(description = "😀".repeat(150))

        assertFailsWith<IllegalArgumentException> {
            createInput(description = "가".repeat(301))
        }
        assertFailsWith<IllegalArgumentException> {
            createInput(description = "😀".repeat(151))
        }
    }

    private fun createInput(description: String) = RoomCreateInput(
        name = "아침운동모임",
        description = description,
        activityDays = listOf("MONDAY"),
        activityTime = "08:00",
    )
}
