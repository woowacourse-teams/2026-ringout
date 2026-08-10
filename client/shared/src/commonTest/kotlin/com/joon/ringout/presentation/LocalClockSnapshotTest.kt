package com.joon.ringout.presentation

import com.joon.ringout.presentation.alarmsetup.AlarmTimePickerValue
import com.joon.ringout.presentation.alarmsetup.toAlarmTimePickerValue
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalClockSnapshotTest {
    @Test
    fun midnightSnapshotIsFormattedAndConvertedToAmTwelve() {
        val time = snapshot(hour = 0, minute = 5).to24HourTimeString()

        assertEquals("00:05", time)
        assertEquals(
            AlarmTimePickerValue(isAm = true, hour = 12, minute = 5),
            time.toAlarmTimePickerValue(),
        )
    }

    @Test
    fun snapshotTimeIsZeroPadded() {
        assertEquals("06:07", snapshot(hour = 6, minute = 7).to24HourTimeString())
        assertEquals("13:07", snapshot(hour = 13, minute = 7).to24HourTimeString())
        assertEquals("23:59", snapshot(hour = 23, minute = 59).to24HourTimeString())
    }

    private fun snapshot(hour: Int, minute: Int) = LocalClockSnapshot(
        dayOfWeek = 0,
        hour = hour,
        minute = minute,
    )
}
