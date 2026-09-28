package com.joon.ringout.presentation.records

import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.MissionDate
import kotlin.test.Test
import kotlin.test.assertEquals

class IosRecordsTimeTest {
    @Test
    fun `종료 전에도 설정된 울림 시각과 비어 있는 종료 시각을 범위로 표시한다`() {
        val record = AlarmUsageRecord(
            key = "one", date = MissionDate.of(2026, 9, 28),
            ringingScheduledAtEpochMillis = 1, ringingStartedAtEpochMillis = 2,
        )
        val times = record.recordTimes { millis, _ -> if (millis == 1L) "07:00" else "07:01" }

        assertEquals("07:00 ~ --:--", times.ringingRange)
        assertEquals("오전 07:00에 울린 알람", times.title)
    }

    @Test
    fun `예정 시각이 없는 과거 기록도 시작과 종료의 범위를 유지한다`() {
        val record = AlarmUsageRecord(
            key = "legacy", date = MissionDate.of(2026, 9, 28), ringingStartedAtEpochMillis = 1,
        )

        assertEquals("07:00 ~ --:--", record.recordTimes { _, _ -> "07:00" }.ringingRange)
    }
}
