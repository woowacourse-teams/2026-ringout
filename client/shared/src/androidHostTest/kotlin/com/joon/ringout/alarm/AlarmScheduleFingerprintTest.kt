package com.joon.ringout.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AlarmScheduleFingerprintTest {
    @Test
    fun `반복 요일의 순서와 중복은 같은 fingerprint로 정규화한다`() {
        val request = alarmRequest(selectedDays = listOf("월", "수", "일"))

        assertEquals(
            request.scheduleFingerprint(),
            request.copy(selectedDays = listOf("일", "월", "수", "월"))
                .scheduleFingerprint(),
        )
    }

    @Test
    fun `저장 예약에 쓰이는 설정이 바뀌면 fingerprint가 바뀐다`() {
        val request = alarmRequest()
        val originalFingerprint = request.scheduleFingerprint()

        listOf(
            request.copy(time = "08:10"),
            request.copy(selectedDays = listOf("화", "목")),
            request.copy(repeatEnabled = false),
            request.copy(limitMinutes = 20),
            request.copy(destinationName = "집"),
            request.copy(destinationAddress = "서울특별시 강남구 테헤란로 1"),
            request.copy(destinationLatitude = 37.0),
            request.copy(destinationLongitude = 127.0),
            request.copy(targetDistanceKm = 1.5),
            request.copy(alarmSoundName = "벨"),
            request.copy(alarmSoundUri = null),
        ).forEach { changed ->
            assertNotEquals(originalFingerprint, changed.scheduleFingerprint())
        }
    }

    private fun alarmRequest(
        selectedDays: List<String> = listOf("월", "금"),
    ) = AlarmScheduleRequest(
        id = "alarm-1",
        time = "07:05",
        selectedDays = selectedDays,
        repeatEnabled = true,
        limitMinutes = 12,
        destinationName = "회사",
        destinationAddress = "서울특별시 중구 세종대로 110",
        destinationLatitude = 37.5665,
        destinationLongitude = 126.978,
        targetDistanceKm = 1.2,
        alarmSoundName = "기본 알람음",
        alarmSoundUri = "content://ringout/alarm/default",
    )
}
