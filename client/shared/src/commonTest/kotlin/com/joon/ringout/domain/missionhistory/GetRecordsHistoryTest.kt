package com.joon.ringout.domain.missionhistory

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetRecordsHistoryTest {
    @Test
    fun `월을 넘는 주는 두 달을 조회하고 같은 날의 식별자 없는 기록도 모두 보존한다`() = runTest {
        val first = entry("2026-08-30", MissionResult.SUCCESS)
        val second = entry("2026-08-30", MissionResult.FAILURE)
        val nextMonth = entry("2026-09-01", MissionResult.SUCCESS, "september")
        val repository = LocalHistoryRepository(
            listOf(first, second, nextMonth, entry("2026-08-29"), entry("2026-09-05")),
        )

        val result = GetRecordsHistory(repository)(
            dates = MissionDate.parse("2026-08-31").weekDates(),
            today = MissionDate.parse("2026-09-04"),
        )

        assertEquals(listOf(first, second, nextMonth), result)
        assertEquals(listOf(MissionYearMonth(2026, 8), MissionYearMonth(2026, 9)), repository.queries)
    }

    @Test
    fun `모든 날짜가 미래인 주는 저장소를 조회하지 않고 빈 기록을 반환한다`() = runTest {
        val repository = LocalHistoryRepository(listOf(entry("2026-10-05")))

        val result = GetRecordsHistory(repository)(
            dates = MissionDate.parse("2026-10-05").weekDates(),
            today = MissionDate.parse("2026-09-28"),
        )

        assertEquals(emptyList(), result)
        assertEquals(emptyList(), repository.queries)
    }
}

private class LocalHistoryRepository(
    private val entries: List<MissionHistoryEntry>,
) : MissionHistoryRepository {
    val queries = mutableListOf<MissionYearMonth>()

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> =
        error("Records must request local history.")

    override suspend fun getLocalHistory(month: MissionYearMonth): List<MissionHistoryEntry> {
        queries += month
        return entries.filter { it.completedAt.belongsTo(month) }
    }

    override suspend fun record(entry: MissionHistoryEntry): Boolean = error("Not used")
}

private fun entry(
    date: String,
    result: MissionResult = MissionResult.SUCCESS,
    occurrenceId: String? = null,
): MissionHistoryEntry = MissionHistoryEntry(result, MissionDate.parse(date), occurrenceId)
