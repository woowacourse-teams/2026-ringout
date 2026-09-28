package com.joon.ringout.alarm

import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmScheduleVersionTest {
    private val original = AlarmScheduleRequest(
        id = "alarm", time = "07:00", selectedDays = listOf("월", "금"), repeatEnabled = true, limitMinutes = 5,
        destinationName = "회사", destinationAddress = "서울", destinationLatitude = 37.5, destinationLongitude = 127.0,
        alarmSoundName = "기본", alarmSoundUri = null,
    )

    @Test
    fun `시간이나 요일을 변경할 때마다 버전을 올리고 원래 값으로 돌아와도 새 버전을 사용한다`() {
        val timeChanged = original.copy(time = "08:00").withScheduleVersion(original)
        val daysChanged = timeChanged.copy(selectedDays = listOf("화")).withScheduleVersion(timeChanged)
        val reverted = original.withScheduleVersion(daysChanged)

        assertEquals(listOf(1L, 2L, 3L, 4L), listOf(original, timeChanged, daysChanged, reverted).map { it.scheduleVersion })
    }

    @Test
    fun `같은 설정이나 요일 순서와 다른 항목만 바꾸면 기존 버전을 유지한다`() {
        val previous = original.copy(scheduleVersion = 7)
        val edited = original.copy(
            selectedDays = listOf("금", "월", "월"), limitMinutes = 10, destinationName = "집", alarmSoundName = "다른 소리",
        )

        assertEquals(7L, original.withScheduleVersion(previous).scheduleVersion)
        assertEquals(7L, edited.withScheduleVersion(previous).scheduleVersion)
        assertEquals(8L, previous.copy(repeatEnabled = false).withScheduleVersion(previous).scheduleVersion)
    }
}
