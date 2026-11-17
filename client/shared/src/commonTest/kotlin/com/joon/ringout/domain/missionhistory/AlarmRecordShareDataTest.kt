package com.joon.ringout.domain.missionhistory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlarmRecordShareDataTest {
    private val date = MissionDate.parse("2026-09-28")

    @Test
    fun `최초 예약 시각과 마지막 성공의 도착 시각 및 식별자로 공유 데이터를 만든다`() {
        val first = record("first", 1_000).copy(ringingScheduledAtEpochMillis = 900)
        val firstSuccess = record("success-a", 2_000).copy(
            result = MissionResult.SUCCESS,
            missionCompletedAtEpochMillis = 3_000,
        )
        val latestSuccess = record("success-b", 4_000).copy(
            result = MissionResult.SUCCESS,
            missionCompletedAtEpochMillis = 5_000,
        )
        val group = listOf(latestSuccess, firstSuccess, first).groupByAlarm().single()

        assertEquals(
            AlarmRecordShareData(group.key, date, "success-b", "success-b", 900, 5_000),
            group.toShareableAlarmRecord(),
        )
    }

    @Test
    fun `최초 예약 시각이 없으면 최초 울림 시작 시각을 사용한다`() {
        val first = record("first", 1_000)
        val success = record("success", 2_000).copy(
            result = MissionResult.SUCCESS,
            missionCompletedAtEpochMillis = 3_000,
        )

        val share = listOf(first, success).groupByAlarm().single().toShareableAlarmRecord()

        assertEquals(1_000L, share?.wakeAtEpochMillis)
        assertEquals(3_000L, share?.arrivedAtEpochMillis)
    }

    @Test
    fun `최초 울림의 시각이 없으면 나중 울림의 시각으로 대체하지 않는다`() {
        val first = record("first", null).copy(ringingStoppedAtEpochMillis = 500)
        val success = record("success", 2_000).copy(
            result = MissionResult.SUCCESS,
            missionCompletedAtEpochMillis = 3_000,
        )

        assertNull(listOf(first, success).groupByAlarm().single().toShareableAlarmRecord())
    }

    @Test
    fun `마지막 성공의 도착 시각이 없으면 이전 성공의 시각으로 대체하지 않는다`() {
        val first = record("first", 1_000).copy(
            result = MissionResult.SUCCESS,
            missionCompletedAtEpochMillis = 1_500,
        )
        val latest = record("latest", 2_000).copy(result = MissionResult.SUCCESS)

        assertNull(listOf(first, latest).groupByAlarm().single().toShareableAlarmRecord())
    }

    @Test
    fun `성공 기록 키를 지정하면 마지막 성공이 아니어도 해당 회차의 도착 시각을 사용한다`() {
        val first = record("first", 1_000).copy(
            result = MissionResult.SUCCESS,
            missionCompletedAtEpochMillis = 1_500,
        )
        val latest = record("latest", 2_000).copy(
            result = MissionResult.SUCCESS,
            missionCompletedAtEpochMillis = 2_500,
        )
        val group = listOf(first, latest).groupByAlarm().single()

        assertEquals(
            AlarmRecordShareData(group.key, date, "first", "first", 1_000, 1_500),
            group.toShareableAlarmRecord(successRecordKey = "first"),
        )
    }

    @Test
    fun `지정한 기록이 없거나 성공이 아니면 다른 성공 기록으로 대체하지 않는다`() {
        val success = record("success", 1_000).copy(
            result = MissionResult.SUCCESS,
            missionCompletedAtEpochMillis = 1_500,
        )
        val failure = record("failure", 2_000).copy(result = MissionResult.FAILURE)
        val group = listOf(success, failure).groupByAlarm().single()

        assertNull(group.toShareableAlarmRecord(successRecordKey = "missing"))
        assertNull(group.toShareableAlarmRecord(successRecordKey = "failure"))
    }

    @Test
    fun `울림이나 강제 종료 기록만 있으면 공유할 수 없다`() {
        val first = record("first", 1_000)
        val failure = record("failure", 2_000).copy(
            result = MissionResult.FAILURE,
            missionCompletedAtEpochMillis = 3_000,
        )

        assertNull(listOf(first, failure).groupByAlarm().single().toShareableAlarmRecord())
    }

    @Test
    fun `발생 식별자가 없는 이전 성공 기록도 두 시각이 있으면 기록 키로 공유할 수 있다`() {
        val legacy = record("history:42", 1_000).copy(
            occurrenceId = null,
            alarmId = null,
            result = MissionResult.SUCCESS,
            missionCompletedAtEpochMillis = 2_000,
        )
        val group = listOf(legacy).groupByAlarm().single()

        assertEquals(
            AlarmRecordShareData(group.key, date, "history:42", null, 1_000, 2_000),
            group.toShareableAlarmRecord(),
        )
    }

    private fun record(key: String, startedAt: Long?) = AlarmUsageRecord(
        key = key,
        date = date,
        occurrenceId = key,
        alarmId = "alarm",
        ringingStartedAtEpochMillis = startedAt,
    )
}
