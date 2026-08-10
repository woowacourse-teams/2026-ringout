package com.joon.ringout.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AlarmScheduleFingerprintTest {
    @Test
    fun canonicalizesRepeatDayOrderAndDuplicates() {
        val request = alarmRequest(selectedDays = listOf("월", "수", "일"))

        assertEquals(
            request.scheduleFingerprint(),
            request.copy(selectedDays = listOf("일", "월", "수", "월"))
                .scheduleFingerprint(),
        )
    }

    @Test
    fun changesWhenPersistedScheduleContentChanges() {
        val request = alarmRequest()
        val originalFingerprint = request.scheduleFingerprint()

        listOf(
            request.copy(time = "08:10"),
            request.copy(repeatEnabled = false),
            request.copy(destinationLatitude = 37.0),
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
