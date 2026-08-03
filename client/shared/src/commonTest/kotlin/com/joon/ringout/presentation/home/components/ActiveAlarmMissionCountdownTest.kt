package com.joon.ringout.presentation.home.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveAlarmMissionCountdownTest {
    @Test
    fun formatsTwelveMinutes() {
        assertEquals("12:00", formatAlarmMissionRemainingTime(720L))
    }

    @Test
    fun formatsOneMinuteAndFiveSeconds() {
        assertEquals("01:05", formatAlarmMissionRemainingTime(65L))
    }

    @Test
    fun formatsExpiredCountdownAsZero() {
        assertEquals("00:00", formatAlarmMissionRemainingTime(0L))
    }

    @Test
    fun roundsAnyRemainingMillisecondUpToOneSecond() {
        assertEquals(
            1L,
            remainingAlarmMissionSeconds(
                expiresAtEpochMillis = 10_001L,
                nowEpochMillis = 10_000L,
            ),
        )
    }
}
