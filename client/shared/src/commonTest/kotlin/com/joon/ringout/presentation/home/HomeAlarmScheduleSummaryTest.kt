package com.joon.ringout.presentation.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeAlarmScheduleSummaryTest {
    @Test
    fun describesTheNearestEnabledRepeatingAlarm() {
        val alarms = listOf(
            alarm(
                time = "06:20",
                isEnabled = true,
                selectedDays = listOf("토", "일"),
            ),
            alarm(
                time = "06:20",
                isEnabled = false,
                selectedDays = listOf("월", "화", "수", "목"),
            ),
        )

        val description = nextAlarmDescription(
            alarms = alarms,
            clock = LocalClockSnapshot(
                dayOfWeek = 4,
                hour = 23,
                minute = 0,
            ),
        )

        assertEquals("7시간 20분 후 알람이 울려요.", description)
    }

    @Test
    fun reportsWhenEveryAlarmIsDisabled() {
        val description = nextAlarmDescription(
            alarms = listOf(
                alarm(
                    time = "06:20",
                    isEnabled = false,
                    selectedDays = listOf("토", "일"),
                ),
            ),
            clock = LocalClockSnapshot(
                dayOfWeek = 4,
                hour = 23,
                minute = 0,
            ),
        )

        assertEquals("켜진 알람이 없습니다.", description)
    }

    @Test
    fun oneTimeAlarmRollsForwardToTomorrow() {
        val description = nextAlarmDescription(
            alarms = listOf(
                alarm(
                    time = "06:20",
                    isEnabled = true,
                    selectedDays = emptyList(),
                    repeatEnabled = false,
                ),
            ),
            clock = LocalClockSnapshot(
                dayOfWeek = 0,
                hour = 7,
                minute = 20,
            ),
        )

        assertEquals("23시간 후 알람이 울려요.", description)
    }

    private fun alarm(
        time: String,
        isEnabled: Boolean,
        selectedDays: List<String>,
        repeatEnabled: Boolean = true,
    ) = HomeAlarm(
        id = "$time-$isEnabled-${selectedDays.joinToString()}",
        time = time,
        days = selectedDays.joinToString(" "),
        destination = "헬스장",
        timeLimitMinutes = 12,
        isEnabled = isEnabled,
        selectedDays = selectedDays,
        repeatEnabled = repeatEnabled,
    )
}
