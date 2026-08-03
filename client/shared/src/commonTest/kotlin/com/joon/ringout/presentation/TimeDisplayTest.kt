package com.joon.ringout.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeDisplayTest {
    @Test
    fun midnightUsesTwelveHourDisplay() {
        assertEquals(
            TwelveHourDisplay(period = "오전", time = "12:05"),
            "00:05".toTwelveHourDisplay(),
        )
    }

    @Test
    fun afternoonUsesTwelveHourDisplay() {
        assertEquals(
            TwelveHourDisplay(period = "오후", time = "01:07"),
            "13:07".toTwelveHourDisplay(),
        )
    }
}
