package com.joon.ringout.domain.missionhistory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AlarmUsageRecordGroupTest {
    private val today = MissionDate.parse("2026-09-28")

    @Test
    fun `같은 알람의 최초 울림과 재울림은 한 카드에서 시간순으로 정렬한다`() {
        val first = ringing("first", "alarm", 1_000)
        val retry = ringing("retry", "alarm", 3_000)

        val group = listOf(retry, first).groupByAlarm().single()

        assertEquals("alarm", group.alarmId)
        assertEquals(listOf(first, retry), group.entries)
        assertEquals(listOf(first).groupByAlarm().single().key, group.key)
    }

    @Test
    fun `같은 시각에 울려도 다른 알람이면 별도 카드로 유지한다`() {
        val groups = listOf(ringing("one", "alarm-a", 1_000), ringing("two", "alarm-b", 1_000)).groupByAlarm()

        assertEquals(2, groups.size)
        assertEquals(setOf("alarm-a", "alarm-b"), groups.map { it.alarmId }.toSet())
        assertNotEquals(groups[0].key, groups[1].key)
    }

    @Test
    fun `같은 알람이라도 울린 날짜가 다르면 별도 카드로 묶는다`() {
        val previous = ringing("previous", "alarm", 1_000).copy(date = MissionDate.parse("2026-09-27"))
        val current = ringing("current", "alarm", 2_000)

        val groups = listOf(previous, current).groupByAlarm()

        assertEquals(2, groups.size)
        assertEquals(listOf(today, previous.date), groups.map { it.date })
        assertNotEquals(groups[0].key, groups[1].key)
    }

    @Test
    fun `알람 식별자가 없는 기존 기록은 다른 기록과 임의로 합치지 않는다`() {
        val groups = listOf(ringing("one", null, 1_000), ringing("two", null, 2_000), ringing("three", "", 3_000)).groupByAlarm()

        assertEquals(3, groups.size)
        assertEquals(listOf("three", "two", "one"), groups.flatMap { it.entries }.map { it.key })
    }

    @Test
    fun `최근 다시 울린 알람 카드를 먼저 보여주고 각 실행의 종료와 도착 시각을 보존한다`() {
        val first = ringing("one", "alarm-a", 1_000).copy(ringingStoppedAtEpochMillis = 1_100)
        val other = ringing("other", "alarm-b", 2_000)
        val retry = ringing("retry", "alarm-a", 3_000).copy(
            ringingStoppedAtEpochMillis = 3_100, result = MissionResult.SUCCESS, missionCompletedAtEpochMillis = 4_000,
        )

        val groups = listOf(first, other, retry).groupByAlarm()

        assertEquals(listOf("alarm-a", "alarm-b"), groups.map { it.alarmId })
        assertEquals(listOf(1_100L, 3_100L), groups.first().entries.map { it.ringingStoppedAtEpochMillis })
        assertEquals(listOf(null, 4_000L), groups.first().entries.map { it.missionCompletedAtEpochMillis })
    }

    private fun ringing(key: String, alarmId: String?, startedAt: Long) = AlarmUsageRecord(
        key = key, date = today, occurrenceId = key, alarmId = alarmId, ringingStartedAtEpochMillis = startedAt,
    )
}
