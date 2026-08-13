package com.joon.ringout.presentation.home

import com.joon.ringout.presentation.LocalClockSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeAlarmScheduleSummaryTest {
    @Test
    fun oneTimeAlarmInTheFutureUsesToday() {
        assertEquals(
            expected = "1시간 30분 후 알람이 울려요.",
            actual = description(
                alarms = listOf(oneTimeAlarm("11:45")),
                dayOfWeek = 0,
                hour = 10,
                minute = 15,
            ),
        )
    }

    @Test
    fun oneTimeAlarmAtOrBeforeNowMovesToTomorrow() {
        assertEquals(
            expected = "24시간 후 알람이 울려요.",
            actual = description(
                alarms = listOf(oneTimeAlarm("10:15")),
                dayOfWeek = 0,
                hour = 10,
                minute = 15,
            ),
        )
        assertEquals(
            expected = "23시간 30분 후 알람이 울려요.",
            actual = description(
                alarms = listOf(oneTimeAlarm("09:45")),
                dayOfWeek = 0,
                hour = 10,
                minute = 15,
            ),
        )
    }

    @Test
    fun repeatingAlarmOnSelectedTodayUsesFutureTimeToday() {
        assertEquals(
            expected = "45분 후 알람이 울려요.",
            actual = description(
                alarms = listOf(repeatingAlarm("11:00", listOf("월", "수"))),
                dayOfWeek = 0,
                hour = 10,
                minute = 15,
            ),
        )
    }

    @Test
    fun repeatingAlarmPastOnSelectedTodayMovesToNextSelectedDay() {
        assertEquals(
            expected = "71시간 후 알람이 울려요.",
            actual = description(
                alarms = listOf(repeatingAlarm("09:00", listOf("월", "목"))),
                dayOfWeek = 0,
                hour = 10,
                minute = 0,
            ),
        )
    }

    @Test
    fun repeatingAlarmWrapsFromSundayAndAcrossAFullWeek() {
        assertEquals(
            expected = "2시간 후 알람이 울려요.",
            actual = description(
                alarms = listOf(repeatingAlarm("01:00", listOf("월"))),
                dayOfWeek = 6,
                hour = 23,
                minute = 0,
            ),
        )
        assertEquals(
            expected = "167시간 후 알람이 울려요.",
            actual = description(
                alarms = listOf(repeatingAlarm("09:00", listOf("월"))),
                dayOfWeek = 0,
                hour = 10,
                minute = 0,
            ),
        )
    }

    @Test
    fun duplicateSelectedDaysDoNotChangeTheResult() {
        val singleDay = description(
            alarms = listOf(repeatingAlarm("11:00", listOf("월"))),
            dayOfWeek = 0,
            hour = 10,
            minute = 0,
        )
        val duplicateDays = description(
            alarms = listOf(repeatingAlarm("11:00", listOf("월", "월", "월"))),
            dayOfWeek = 0,
            hour = 10,
            minute = 0,
        )

        assertEquals(singleDay, duplicateDays)
        assertEquals("1시간 후 알람이 울려요.", duplicateDays)
    }

    @Test
    fun disabledAndInvalidTimeAlarmsAreExcluded() {
        assertEquals(
            expected = "1시간 후 알람이 울려요.",
            actual = description(
                alarms = listOf(
                    oneTimeAlarm(time = "23:59", isEnabled = false),
                    oneTimeAlarm(time = "25:99"),
                    oneTimeAlarm(time = "00:58"),
                ),
                dayOfWeek = 0,
                hour = 23,
                minute = 58,
            ),
        )
    }

    @Test
    fun selectsTheActuallyNearestEnabledAlarm() {
        assertEquals(
            expected = "30분 후 알람이 울려요.",
            actual = description(
                alarms = listOf(
                    oneTimeAlarm("06:00"),
                    repeatingAlarm("01:00", listOf("화")),
                    repeatingAlarm("23:30", listOf("월")),
                ),
                dayOfWeek = 0,
                hour = 23,
                minute = 0,
            ),
        )
    }

    private fun description(
        alarms: List<HomeAlarm>,
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
    ): String = nextAlarmDescription(
        alarms = alarms,
        clock = LocalClockSnapshot(
            dayOfWeek = dayOfWeek,
            hour = hour,
            minute = minute,
        ),
    )

    private fun oneTimeAlarm(
        time: String,
        isEnabled: Boolean = true,
    ): HomeAlarm = alarm(
        time = time,
        isEnabled = isEnabled,
        selectedDays = emptyList(),
        repeatEnabled = false,
    )

    private fun repeatingAlarm(
        time: String,
        selectedDays: List<String>,
        isEnabled: Boolean = true,
    ): HomeAlarm = alarm(
        time = time,
        isEnabled = isEnabled,
        selectedDays = selectedDays,
        repeatEnabled = true,
    )

    private fun alarm(
        time: String,
        isEnabled: Boolean,
        selectedDays: List<String>,
        repeatEnabled: Boolean,
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
