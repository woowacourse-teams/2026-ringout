package com.joon.ringout.presentation.ringing

import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmRingingScreenTest {
    @Test
    fun removesLeadingZeroFromAlarmHour() {
        assertEquals("7:00", formatAlarmDisplayTime("07:00"))
    }

    @Test
    fun keepsMidnightHourReadable() {
        assertEquals("0:05", formatAlarmDisplayTime("00:05"))
    }

    @Test
    fun keepsUnknownTimeAsProvided() {
        assertEquals("알 수 없음", formatAlarmDisplayTime("알 수 없음"))
    }
}
