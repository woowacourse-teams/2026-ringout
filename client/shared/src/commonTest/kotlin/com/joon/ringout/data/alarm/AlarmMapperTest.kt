package com.joon.ringout.data.alarm

import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AlarmMapperTest {
    @Test
    fun roundTripsEveryStoredAlarmFieldAndCanonicalizesRepeatDays() {
        val savedAlarm = savedAlarm(
            selectedDays = listOf("일", "월", "수", "월"),
            repeatEnabled = false,
            enabled = false,
        )

        val stored = savedAlarm.toAlarmWithRepeatDays()
        val restored = stored.toSavedAlarmSchedule()

        assertEquals(listOf(1, 3, 7), stored.repeatDays.map { it.dayOfWeek })
        assertEquals(
            savedAlarm.copy(
                request = savedAlarm.request.copy(selectedDays = listOf("월", "수", "일")),
            ),
            restored,
        )
    }

    @Test
    fun preservesRepeatEnabledIndependentlyFromSelectedDays() {
        val oneShotLikeAlarm = savedAlarm(
            selectedDays = emptyList(),
            repeatEnabled = true,
        )

        assertEquals(
            oneShotLikeAlarm,
            oneShotLikeAlarm.toAlarmWithRepeatDays().toSavedAlarmSchedule(),
        )
    }

    @Test
    fun validationReturnsTheOriginalValueWhenItIsValid() {
        val request = savedAlarm().request
        val savedAlarm = SavedAlarmSchedule(request = request, enabled = true)

        assertSame(request, request.validateForStorage())
        assertSame(savedAlarm, savedAlarm.validateForStorage())
    }

    @Test
    fun rejectsNonCanonicalTimesAndUnknownRepeatDays() {
        listOf("7:05", "24:00", "12:60", "not-a-time").forEach { time ->
            assertFailsWith<IllegalArgumentException> {
                savedAlarm(time = time).validateForStorage()
            }
        }

        assertFailsWith<IllegalArgumentException> {
            savedAlarm(selectedDays = listOf("월", "Mon")).validateForStorage()
        }
    }

    @Test
    fun rejectsValuesThatCannotSafelyRepresentAnAlarmMission() {
        val invalidRequests = listOf(
            savedAlarm(limitMinutes = 0),
            savedAlarm(destinationLatitude = Double.NaN),
            savedAlarm(destinationLatitude = 90.0001),
            savedAlarm(destinationLongitude = Double.POSITIVE_INFINITY),
            savedAlarm(destinationLongitude = -180.0001),
            savedAlarm(targetDistanceKm = 0.0),
        )

        invalidRequests.forEach { alarm ->
            assertFailsWith<IllegalArgumentException> {
                alarm.validateForStorage()
            }
        }
    }

    @Test
    fun rejectsCorruptPersistedRepeatDays() {
        val valid = savedAlarm().toAlarmWithRepeatDays()

        assertFailsWith<IllegalArgumentException> {
            valid.copy(
                repeatDays = listOf(
                    AlarmRepeatDayEntity(alarmId = valid.alarm.id, dayOfWeek = 8),
                ),
            ).toSavedAlarmSchedule()
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(
                repeatDays = listOf(
                    AlarmRepeatDayEntity(alarmId = "another-alarm", dayOfWeek = 1),
                ),
            ).toSavedAlarmSchedule()
        }
    }

    private fun savedAlarm(
        time: String = "07:05",
        selectedDays: List<String> = listOf("월", "금"),
        repeatEnabled: Boolean = true,
        limitMinutes: Int = 12,
        destinationLatitude: Double = 37.5665,
        destinationLongitude: Double = 126.978,
        targetDistanceKm: Double = 1.2,
        enabled: Boolean = true,
    ) = SavedAlarmSchedule(
        request = AlarmScheduleRequest(
            id = "alarm-1",
            time = time,
            selectedDays = selectedDays,
            repeatEnabled = repeatEnabled,
            limitMinutes = limitMinutes,
            destinationName = "회사",
            destinationAddress = "서울특별시 중구 세종대로 110",
            destinationLatitude = destinationLatitude,
            destinationLongitude = destinationLongitude,
            targetDistanceKm = targetDistanceKm,
            alarmSoundName = "기본 알람음",
            alarmSoundUri = "content://ringout/alarm/default",
        ),
        enabled = enabled,
    )
}
