package com.joon.ringout.presentation.records

import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.MissionDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidRecordsTimeTest {
    @Test
    fun `종료 전에는 기존처럼 울림 시각만 표시하고 종료 후 범위를 표시한다`() {
        val ringing = AlarmUsageRecord(
            key = "one", date = MissionDate.of(2026, 9, 28), ringingStartedAtEpochMillis = 1,
        )
        val format: (Long, MissionDate) -> String = { millis, _ -> if (millis == 1L) "07:00" else "07:02" }

        assertEquals("오전 07:00", ringing.recordTimes(format).ringingRange)
        assertEquals("오전 07:00 ~ 오전 07:02", ringing.copy(ringingStoppedAtEpochMillis = 2).recordTimes(format).ringingRange)
    }
}
