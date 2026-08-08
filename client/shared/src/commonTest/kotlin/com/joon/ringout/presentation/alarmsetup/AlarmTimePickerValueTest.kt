package com.joon.ringout.presentation.alarmsetup

import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmTimePickerValueTest {
    @Test
    fun midnightIsConvertedToAmTwelve() {
        assertEquals(
            AlarmTimePickerValue(isAm = true, hour = 12, minute = 5),
            "00:05".toAlarmTimePickerValue(),
        )
    }

    @Test
    fun afternoonTimeIsConvertedToTwelveHourValue() {
        assertEquals(
            AlarmTimePickerValue(isAm = false, hour = 1, minute = 7),
            "13:07".toAlarmTimePickerValue(),
        )
    }

    @Test
    fun amTwelveIsSavedAsMidnight() {
        assertEquals(
            "00:05",
            AlarmTimePickerValue(isAm = true, hour = 12, minute = 5).to24HourString(),
        )
    }

    @Test
    fun pmTwelveIsSavedAsNoon() {
        assertEquals(
            "12:05",
            AlarmTimePickerValue(isAm = false, hour = 12, minute = 5).to24HourString(),
        )
    }

    @Test
    fun eachPickerValueIsCombinedOnlyWhenSaving() {
        assertEquals(
            "18:20",
            AlarmTimePickerValue(isAm = false, hour = 6, minute = 20).to24HourString(),
        )
    }
}
