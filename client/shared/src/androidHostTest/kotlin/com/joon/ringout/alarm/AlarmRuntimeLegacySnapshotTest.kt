package com.joon.ringout.alarm

import android.content.Intent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmRuntimeLegacySnapshotTest {
    @Test
    fun `구 broadcast 스냅샷은 label과 예약 값을 일치시킨다`() {
        val request = alarmRequest()
        val intent = Intent().putAlarmExtras(request)

        assertTrue(intent.matchesLegacyAlarmScheduleSnapshot(request))
        assertFalse(
            intent.putExtra(AlarmRuntime.EXTRA_ALARM_TIME, "08:00")
                .matchesLegacyAlarmScheduleSnapshot(request),
        )
    }

    @Test
    fun `비어 있던 기존 목적지 이름은 마이그레이션 주소 fallback과 일치한다`() {
        val request = alarmRequest(destinationLabel = "주소 정보 없음")
        val intent = Intent().putAlarmExtras(request)
            .putExtra(AlarmRuntime.EXTRA_DESTINATION_NAME, "")

        assertTrue(intent.matchesLegacyAlarmScheduleSnapshot(request))
    }
}

private fun alarmRequest(destinationLabel: String = "회사") = AlarmScheduleRequest(
    id = "alarm-1",
    time = "07:05",
    selectedDays = listOf("월", "금"),
    repeatEnabled = true,
    limitMinutes = 12,
    destinationLabel = destinationLabel,
    destinationLatitude = 37.5665,
    destinationLongitude = 126.978,
    targetDistanceKm = 1.2,
    alarmSoundName = "기본 알람음",
    alarmSoundUri = null,
)
