package com.joon.ringout.data.missionhistory

import com.joon.ringout.data.alarmoccurrence.CachedAlarmOccurrenceEntity
import com.joon.ringout.data.alarmoccurrence.CachedAlarmOccurrenceRow
import com.joon.ringout.data.alarmoccurrence.CachedAlarmRingingEntity
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.groupByAlarm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordsHistoryMergeTest {
    @Test
    fun `연결된 최초 울림과 재울림을 서버 기준일에 한 번씩 표시하고 전송 대기 종료를 보존한다`() {
        val snapshot = cached("server", null, null, listOf(ring("server", null, 100), ring("server", "synced", 200)))
        val local = listOf(
            local("initial", "server", null, startedAt = 100, stoppedAt = 110),
            local("repeat-synced", "server", "synced", startedAt = 200),
            local("repeat-pending", "server", "pending", startedAt = 300),
            local("terminal", "server", "terminal", result = MissionResult.FAILURE, endedAt = 350),
        )

        val card = mergeRecords(local, listOf(snapshot), setOf(DECEMBER, JANUARY)).groupByAlarm().single()

        assertEquals(DECEMBER, card.date)
        assertEquals(listOf(100L, 200L, 300L), card.entries.map { it.ringingStartedAtEpochMillis })
        assertEquals(3, card.entries.sumOf { it.ringingCount })
        assertEquals(110L, card.entries.first().ringingStoppedAtEpochMillis)
        assertEquals(listOf(null, null, MissionResult.FAILURE), card.entries.map { it.result })
        assertEquals(350L, card.entries.last().missionCompletedAtEpochMillis)
        assertEquals("23:55", card.entries.first().alarmTime)
    }

    @Test
    fun `같은 알람의 서버 실행을 별도 카드로 만들고 연결되지 않은 레거시 기록을 보존한다`() {
        val local = listOf(local("legacy", null, null, result = MissionResult.FAILURE, endedAt = 400))
        val arrived = cached("first", "ARRIVED", 110, listOf(ring("first", null, 100)))
        val ended = cached("second", "FORCE_ENDED", 210, listOf(ring("second", null, 200)))
        val ringing = cached("third", null, null, listOf(ring("third", null, 300)))

        val records = mergeRecords(local, listOf(arrived, ended, ringing), setOf(DECEMBER, JANUARY))
        val cards = records.groupByAlarm()

        assertEquals(4, cards.size)
        assertEquals(3, records.sumOf { it.ringingCount })
        assertEquals(MissionResult.SUCCESS, cards[0].entries.single().result)
        assertEquals(MissionResult.FAILURE, cards[1].entries.single().result)
        assertNull(cards[2].entries.single().result)
        assertEquals(JANUARY, cards[3].date)
    }

    private fun cached(
        id: String,
        endType: String?,
        endedAt: Long?,
        ringings: List<CachedAlarmRingingEntity>,
    ) = CachedAlarmOccurrenceRow(
        CachedAlarmOccurrenceEntity("owner", "2026-12", id, "alarm", DECEMBER.iso8601, "23:55", endType, endedAt),
        ringings,
    )

    private fun ring(id: String, eventId: String?, at: Long) = CachedAlarmRingingEntity(
        "owner", id, eventId?.let { "REPEAT:$it" } ?: "INITIAL", eventId, at, null,
    )

    private fun local(
        id: String,
        serverId: String?,
        eventId: String?,
        startedAt: Long? = null,
        stoppedAt: Long? = null,
        result: MissionResult? = null,
        endedAt: Long? = null,
    ) = AlarmUsageRecordWithCacheLink(
        record = AlarmUsageRecordRow(
            key = "occurrence:$id",
            date = JANUARY.iso8601,
            occurrenceId = id,
            result = result?.persistedValue,
            ringingStartedAtEpochMillis = startedAt,
            ringingStoppedAtEpochMillis = stoppedAt,
            missionCompletedAtEpochMillis = endedAt,
            isRingingStartObserved = startedAt != null,
            alarmId = "alarm",
            ringingScheduledAtEpochMillis = null,
            scheduleVersion = 1,
            ringingCount = if (startedAt == null) 0 else 1,
        ),
        serverOccurrenceId = serverId,
        eventId = eventId,
    )

    private companion object {
        val DECEMBER = MissionDate.of(2026, 12, 31)
        val JANUARY = MissionDate.of(2027, 1, 1)
    }
}
