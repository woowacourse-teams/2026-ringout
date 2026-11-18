package com.joon.ringout.data.alarm

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyAlarmPreferencesMigratorTest {
    @Test
    fun `기존 JSON의 이름과 주소 필드를 destinationLabel로 변환한다`() {
        assertEquals("회사 별칭", decodeLegacyAlarm(legacyAlarm(name = "회사 별칭", address = "서울 주소")).request.destinationLabel)
        assertEquals("서울 주소", decodeLegacyAlarm(legacyAlarm(name = "   ", address = "서울 주소")).request.destinationLabel)
        assertEquals("주소 정보 없음", decodeLegacyAlarm(legacyAlarm(name = "   ", address = "   ")).request.destinationLabel)
    }

    private fun legacyAlarm(name: String, address: String) = """
        {
          "id": "legacy-alarm",
          "time": "07:05",
          "days": ["월", "금"],
          "repeatEnabled": true,
          "limitMinutes": 12,
          "destinationName": "$name",
          "destinationAddress": "$address",
          "destinationLatitude": 37.5665,
          "destinationLongitude": 126.978,
          "targetDistanceKm": 1.2,
          "soundName": "기본 알람음",
          "enabled": true
        }
    """.trimIndent()
}
