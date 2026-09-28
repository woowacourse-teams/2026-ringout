package com.joon.ringout.presentation.records

import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.toAlarmUsageRecord
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionResult
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordsTimeTest {
    private val date = MissionDate.of(2026, 9, 28)
    private val format: (Long, MissionDate) -> String = { millis, _ ->
        mapOf(1L to "9/27 23:58", 2L to "00:03", 3L to "00:15").getValue(millis)
    }

    @Test
    fun `종료 전에는 울린 시각만 표시하고 도착 시각은 비워 둔다`() {
        val times = AlarmUsageRecord("one", date, ringingStartedAtEpochMillis = 2).recordTimes(format)

        assertEquals("00:03에 울린 알람", times.title)
        assertEquals("00:03", times.ringingRange)
        kotlin.test.assertNull(times.completedTime)
    }

    @Test
    fun `저장된 시작과 종료와 완료 시각을 카드에 표시한다`() {
        val times = MissionHistoryEntry(MissionResult.SUCCESS, date, "one", 1, 2, 3).toAlarmUsageRecord().recordTimes(format)
        assertEquals("9/27 23:58 ~ 00:03", times.ringingRange)
        assertEquals("울림 시작 ~ 울림 종료", times.ringingDescription)
        assertEquals("미션 완료 00:15", times.completedDescription)
    }

    @Test
    fun `시각이 없는 기존 기록은 시간을 만들어 표시하지 않는다`() {
        val times = MissionHistoryEntry(MissionResult.SUCCESS, date).toAlarmUsageRecord().recordTimes(format)
        assertEquals("--:-- ~ --:--", times.ringingRange)
        assertEquals("울림·종료 시각 기록 없음", times.ringingDescription)
        assertEquals("완료 시각 기록 없음", times.completedDescription)
    }

    @Test
    fun `확인하지 못한 시작 시각은 비우고 저장된 종료 시각만 표시한다`() {
        val times = MissionHistoryEntry(MissionResult.FAILURE, date, "one", null, 2, 3).toAlarmUsageRecord().recordTimes(format)
        assertEquals("--:-- ~ 00:03", times.ringingRange)
        assertEquals("울림 시작 시각 기록 없음", times.ringingDescription)
        assertEquals("미션 종료 00:15", times.completedDescription)
    }

    @Test
    fun `앱에서 관찰한 울림 시각은 확인 시각으로 구분한다`() {
        val times = MissionHistoryEntry(MissionResult.SUCCESS, date, "one", 1, 2, 3, true).toAlarmUsageRecord().recordTimes(format)
        assertEquals("울림 확인 ~ 울림 종료", times.ringingDescription)
    }
}
