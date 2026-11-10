package com.joon.ringout.domain.missionhistory

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetAlarmRecordGroupTest {
    private val date = MissionDate.parse("2026-09-30")
    private val records = listOf(
        AlarmUsageRecord("occurrence:first", date, "first", alarmId = "alarm", ringingStartedAtEpochMillis = 100),
        AlarmUsageRecord("occurrence:success", date, "success", MissionResult.SUCCESS, alarmId = "alarm", missionCompletedAtEpochMillis = 200),
        AlarmUsageRecord("occurrence:other", date, "other", alarmId = "alarm", scheduleVersion = 2),
        AlarmUsageRecord("history:old", date, result = MissionResult.SUCCESS, missionCompletedAtEpochMillis = 300),
    )

    @Test
    fun `회차로 조회하면 같은 알람과 예약 버전의 그룹만 반환한다`() = runTest {
        val result = GetAlarmRecordGroup(TestRecordRepository(records)).byOccurrenceId("success")

        assertEquals(listOf("first", "success"), result?.entries?.map { it.occurrenceId })
    }

    @Test
    fun `날짜와 그룹 키로 조회하면 회차 식별자가 없는 이전 기록도 찾는다`() = runTest {
        val group = records.groupByAlarm().single { it.entries.singleOrNull()?.key == "history:old" }
        val lookup = GetAlarmRecordGroup(TestRecordRepository(records))

        assertEquals(group, lookup.byDateAndKey(date, group.key))
        assertNull(lookup.byDateAndKey(MissionDate.parse("2026-10-01"), group.key))
    }

    @Test
    fun `없는 회차와 빈 식별자는 그룹을 찾지 못한다`() = runTest {
        val repository = TestRecordRepository(records)
        val lookup = GetAlarmRecordGroup(repository)

        assertNull(lookup.byOccurrenceId("missing"))
        assertNull(lookup.byOccurrenceId(" "))
        assertEquals(1, repository.occurrenceQueries)
    }
}

private class TestRecordRepository(private val records: List<AlarmUsageRecord>) : MissionHistoryRepository {
    var occurrenceQueries = 0

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> = error("Not used")
    override suspend fun record(entry: MissionHistoryEntry): Boolean = error("Not used")
    override suspend fun getLocalRecords(month: MissionYearMonth): List<AlarmUsageRecord> =
        records.filter { it.date.belongsTo(month) }

    override suspend fun getLocalRecordsOnOccurrenceDate(occurrenceId: String): List<AlarmUsageRecord> {
        occurrenceQueries++
        val date = records.firstOrNull { it.occurrenceId == occurrenceId }?.date ?: return emptyList()
        return records.filter { it.date == date }
    }
}
