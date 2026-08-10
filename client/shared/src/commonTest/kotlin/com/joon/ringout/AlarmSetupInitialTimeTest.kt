package com.joon.ringout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AlarmSetupInitialTimeTest {
    @Test
    fun newAlarmUsesCapturedLocalTime() {
        assertEquals(
            "13:07",
            alarmSetupInitialTime(
                editingAlarmId = null,
                editingAlarmTime = null,
                newAlarmInitialTime = "13:07",
            ),
        )
    }

    @Test
    fun editingAlarmUsesSavedTime() {
        assertEquals(
            "08:30",
            alarmSetupInitialTime(
                editingAlarmId = "alarm-1",
                editingAlarmTime = "08:30",
                newAlarmInitialTime = "13:07",
            ),
        )
    }

    @Test
    fun unloadedEditingAlarmDoesNotUseCapturedNewAlarmTime() {
        val capturedNewAlarmTime = "13:07"
        val initialTime = alarmSetupInitialTime(
            editingAlarmId = "alarm-1",
            editingAlarmTime = null,
            newAlarmInitialTime = capturedNewAlarmTime,
        )

        assertEquals("06:20", initialTime)
        assertNotEquals(capturedNewAlarmTime, initialTime)
    }
}
