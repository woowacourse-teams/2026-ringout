package com.joon.ringout.presentation.home

import com.joon.ringout.presentation.LocalClockSnapshot
import com.joon.ringout.presentation.home.model.HomeAlarm
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeAlarmScheduleSummaryTest {
    @Test
    fun `다음 알람까지 남은 시간을 시와 분 문구로 표시한다`() {
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
    fun `다음 알람까지 한 시간 미만이면 분 문구만 표시한다`() {
        assertEquals(
            expected = "45분 후 알람이 울려요.",
            actual = description(
                alarms = listOf(oneTimeAlarm("11:00")),
                dayOfWeek = 0,
                hour = 10,
                minute = 15,
            ),
        )
    }

    @Test
    fun `한국어 반복 요일을 도메인 요일로 변환한다`() {
        assertEquals(
            expected = "2시간 후 알람이 울려요.",
            actual = description(
                alarms = listOf(
                    repeatingAlarm(time = "01:00", selectedDays = listOf("월")),
                ),
                dayOfWeek = 6,
                hour = 23,
                minute = 0,
            ),
        )
    }

    @Test
    fun `켜진 유효 알람이 없으면 안내 문구를 표시한다`() {
        assertEquals(
            expected = "켜진 알람이 없습니다.",
            actual = description(
                alarms = listOf(
                    oneTimeAlarm(time = "23:59", isEnabled = false),
                    oneTimeAlarm(time = "25:99"),
                ),
                dayOfWeek = 0,
                hour = 23,
                minute = 58,
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
    ): HomeAlarm = alarm(
        time = time,
        isEnabled = true,
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
