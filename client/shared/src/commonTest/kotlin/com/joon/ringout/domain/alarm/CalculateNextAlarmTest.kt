package com.joon.ringout.domain.alarm

import com.joon.ringout.domain.alarm.CalculateNextAlarm.Alarm
import com.joon.ringout.domain.alarm.CalculateNextAlarm.CurrentTime
import com.joon.ringout.domain.alarm.CalculateNextAlarm.Day
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculateNextAlarmTest {
    private val calculateNextAlarm = CalculateNextAlarm()

    @Test
    fun `한 번 울리는 미래 알람은 오늘 남은 시간을 계산한다`() {
        val result = calculateNextAlarm(
            alarms = listOf(alarm(hour = 11, minute = 45)),
            currentTime = currentTime(day = Day.MONDAY, hour = 10, minute = 15),
        )

        assertEquals(90, result?.minutesUntil)
    }

    @Test
    fun `한 번 울리는 알람 시각이 현재와 같거나 이전이면 다음 날로 계산한다`() {
        val sameTime = calculateNextAlarm(
            alarms = listOf(alarm(hour = 10, minute = 15)),
            currentTime = currentTime(day = Day.MONDAY, hour = 10, minute = 15),
        )
        val previousTime = calculateNextAlarm(
            alarms = listOf(alarm(hour = 9, minute = 45)),
            currentTime = currentTime(day = Day.MONDAY, hour = 10, minute = 15),
        )

        assertEquals(24 * 60, sameTime?.minutesUntil)
        assertEquals(23 * 60 + 30, previousTime?.minutesUntil)
    }

    @Test
    fun `오늘 반복 알람이 남아 있으면 오늘 시각으로 계산한다`() {
        val result = calculateNextAlarm(
            alarms = listOf(
                alarm(
                    hour = 11,
                    repeatDays = setOf(Day.MONDAY, Day.WEDNESDAY),
                ),
            ),
            currentTime = currentTime(day = Day.MONDAY, hour = 10, minute = 15),
        )

        assertEquals(45, result?.minutesUntil)
    }

    @Test
    fun `오늘 반복 알람 시각이 지났으면 다음 선택 요일로 계산한다`() {
        val result = calculateNextAlarm(
            alarms = listOf(
                alarm(
                    hour = 9,
                    repeatDays = setOf(Day.MONDAY, Day.THURSDAY),
                ),
            ),
            currentTime = currentTime(day = Day.MONDAY, hour = 10),
        )

        assertEquals(71 * 60, result?.minutesUntil)
    }

    @Test
    fun `반복 알람은 일요일에서 다음 주 요일까지 순환해서 계산한다`() {
        val nextMonday = calculateNextAlarm(
            alarms = listOf(
                alarm(hour = 1, repeatDays = setOf(Day.MONDAY)),
            ),
            currentTime = currentTime(day = Day.SUNDAY, hour = 23),
        )
        val followingMonday = calculateNextAlarm(
            alarms = listOf(
                alarm(hour = 9, repeatDays = setOf(Day.MONDAY)),
            ),
            currentTime = currentTime(day = Day.MONDAY, hour = 10),
        )

        assertEquals(2 * 60, nextMonday?.minutesUntil)
        assertEquals(167 * 60, followingMonday?.minutesUntil)
    }

    @Test
    fun `꺼진 알람만 있으면 다음 알람이 없다`() {
        val result = calculateNextAlarm(
            alarms = listOf(alarm(hour = 11, isEnabled = false)),
            currentTime = currentTime(day = Day.MONDAY, hour = 10),
        )

        assertNull(result)
    }

    @Test
    fun `여러 알람 중 실제로 가장 가까운 알람을 반환한다`() {
        val result = calculateNextAlarm(
            alarms = listOf(
                alarm(id = "tomorrow", hour = 6),
                alarm(
                    id = "tuesday",
                    hour = 1,
                    repeatDays = setOf(Day.TUESDAY),
                ),
                alarm(
                    id = "today",
                    hour = 23,
                    minute = 30,
                    repeatDays = setOf(Day.MONDAY),
                ),
            ),
            currentTime = currentTime(day = Day.MONDAY, hour = 23),
        )

        assertEquals("today", result?.alarmId)
        assertEquals(30, result?.minutesUntil)
    }

    private fun alarm(
        id: String = "alarm",
        hour: Int,
        minute: Int = 0,
        repeatDays: Set<Day> = emptySet(),
        isEnabled: Boolean = true,
    ) = Alarm(
        id = id,
        hour = hour,
        minute = minute,
        repeatDays = repeatDays,
        isEnabled = isEnabled,
    )

    private fun currentTime(
        day: Day,
        hour: Int,
        minute: Int = 0,
    ) = CurrentTime(
        day = day,
        hour = hour,
        minute = minute,
    )
}
