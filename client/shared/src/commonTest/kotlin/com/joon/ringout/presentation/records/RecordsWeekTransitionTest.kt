package com.joon.ringout.presentation.records

import com.joon.ringout.domain.alarmactivity.AlarmActivityRepository
import com.joon.ringout.domain.alarmactivity.AlarmActivitySummary
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.GetRecordsHistory
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.plusDays
import com.joon.ringout.domain.missionhistory.weekDates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsWeekTransitionTest {
    @Test
    fun `주 이동 응답이 오백 밀리초 이내면 로딩 없이 즉시 함께 전환한다`() = runTest {
        for (responseMillis in listOf(0L, 300L, 499L, 500L)) {
            val fixture = WeekTransitionFixture(backgroundScope, testScheduler.timeSource)
            fixture.viewModel.refresh()
            runCurrent()
            val original = fixture.viewModel.uiState.value
            val pending = fixture.prepareResponse()

            fixture.viewModel.previousWeek()
            runCurrent()
            assertEquals(original.copy(isWeekChanging = true), fixture.viewModel.uiState.value)
            advanceTimeBy(responseMillis)
            pending.complete(fixture.lastWeek)
            runCurrent()

            assertNewWeek(fixture.viewModel.uiState.value, fixture.lastWeek)
            advanceTimeBy(1_000)
            runCurrent()
            assertFalse(fixture.viewModel.uiState.value.showWeekLoadingIndicator)
        }
    }

    @Test
    fun `육백 밀리초에 응답이 와도 칠백오십 밀리초까지 기존 화면과 로딩을 유지한다`() = runTest {
        val fixture = WeekTransitionFixture(backgroundScope, testScheduler.timeSource)
        fixture.viewModel.refresh()
        runCurrent()
        val original = fixture.viewModel.uiState.value
        val pending = fixture.prepareResponse()
        fixture.viewModel.previousWeek()
        runCurrent()
        advanceTimeBy(499)
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.showWeekLoadingIndicator)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.showWeekLoadingIndicator)

        advanceTimeBy(100)
        pending.complete(fixture.lastWeek)
        runCurrent()
        assertEquals(original.copy(isWeekChanging = true, showWeekLoadingIndicator = true), fixture.viewModel.uiState.value)
        advanceTimeBy(149)
        runCurrent()
        assertEquals(original.selectedDate, fixture.viewModel.uiState.value.selectedDate)
        assertTrue(fixture.viewModel.uiState.value.showWeekLoadingIndicator)

        advanceTimeBy(1)
        runCurrent()
        assertNewWeek(fixture.viewModel.uiState.value, fixture.lastWeek)
    }

    @Test
    fun `칠백오십 밀리초 이후 응답은 추가 대기 없이 로딩을 닫고 전환한다`() = runTest {
        for (responseMillis in listOf(750L, 1_200L)) {
            val fixture = WeekTransitionFixture(backgroundScope, testScheduler.timeSource)
            fixture.viewModel.refresh()
            runCurrent()
            val pending = fixture.prepareResponse()
            fixture.viewModel.previousWeek()
            runCurrent()
            advanceTimeBy(responseMillis)
            runCurrent()
            assertTrue(fixture.viewModel.uiState.value.showWeekLoadingIndicator)

            pending.complete(fixture.lastWeek)
            runCurrent()

            assertNewWeek(fixture.viewModel.uiState.value, fixture.lastWeek)
        }
    }

    @Test
    fun `횟수만 먼저 도착해도 카드가 준비될 때까지 이전 날짜와 횟수를 유지한다`() = runTest {
        val fixture = WeekTransitionFixture(backgroundScope, testScheduler.timeSource)
        fixture.viewModel.refresh()
        runCurrent()
        val original = fixture.viewModel.uiState.value
        val pending = fixture.prepareResponse()
        fixture.viewModel.previousWeek()
        runCurrent()
        advanceTimeBy(100)
        pending.summaries.tryEmit(weekSummaries(fixture.lastWeek, 2))
        runCurrent()
        assertEquals(original.copy(isWeekChanging = true), fixture.viewModel.uiState.value)

        advanceTimeBy(800)
        runCurrent()
        pending.history.tryEmit(listOf(record(fixture.lastWeek)))
        runCurrent()

        assertNewWeek(fixture.viewModel.uiState.value, fixture.lastWeek)
    }

    @Test
    fun `연속 주 이동은 마지막 요청을 기준으로 이동하고 이전 종료 타이머를 취소한다`() = runTest {
        val fixture = WeekTransitionFixture(backgroundScope, testScheduler.timeSource)
        fixture.viewModel.refresh()
        runCurrent()
        val first = fixture.prepareResponse()
        fixture.viewModel.previousWeek()
        runCurrent()
        advanceTimeBy(600)
        runCurrent()
        first.complete(fixture.lastWeek)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.showWeekLoadingIndicator)

        val second = fixture.prepareResponse()
        fixture.viewModel.previousWeek()
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.showWeekLoadingIndicator)
        advanceTimeBy(100)
        val target = fixture.lastWeek.plusDays(-7)
        second.complete(target)
        runCurrent()
        assertNewWeek(fixture.viewModel.uiState.value, target)

        first.complete(fixture.lastWeek, count = 9)
        advanceTimeBy(1_000)
        runCurrent()
        assertNewWeek(fixture.viewModel.uiState.value, target)
    }

    @Test
    fun `느린 주 조회가 실패해도 최소 표시 시간을 지키고 기존 기록에서 재시도한다`() = runTest {
        val fixture = WeekTransitionFixture(backgroundScope, testScheduler.timeSource)
        fixture.viewModel.refresh()
        runCurrent()
        val original = fixture.viewModel.uiState.value
        fixture.prepareResponse()
        fixture.historyStream = flow {
            delay(600)
            error("Database unavailable")
        }
        fixture.viewModel.previousWeek()
        runCurrent()
        advanceTimeBy(600)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.showWeekLoadingIndicator)
        assertNull(fixture.viewModel.uiState.value.weekChangeErrorMessage)
        advanceTimeBy(150)
        runCurrent()
        val failed = fixture.viewModel.uiState.value
        assertNotNull(failed.weekChangeErrorMessage)
        assertEquals(original.records, failed.records)
        assertEquals(original.selectedDate, failed.selectedDate)
        assertEquals(original.activitySummary, failed.activitySummary)
        assertFalse(failed.showWeekLoadingIndicator)
        assertFalse(failed.isWeekChanging)

        val retry = fixture.prepareResponse()
        fixture.viewModel.retry()
        runCurrent()
        retry.complete(fixture.lastWeek)
        runCurrent()
        assertNewWeek(fixture.viewModel.uiState.value, fixture.lastWeek)
    }

    @Test
    fun `주 전환 후에도 실시간 변경과 같은 주의 캐시 선택을 유지한다`() = runTest {
        val fixture = WeekTransitionFixture(backgroundScope, testScheduler.timeSource)
        fixture.viewModel.refresh()
        runCurrent()
        val pending = fixture.prepareResponse()
        fixture.viewModel.previousWeek()
        runCurrent()
        pending.complete(fixture.lastWeek)
        runCurrent()
        assertNewWeek(fixture.viewModel.uiState.value, fixture.lastWeek)
        val nextDate = fixture.lastWeek.plusDays(1)
        pending.history.tryEmit(listOf(record(fixture.lastWeek), record(nextDate)))
        pending.summaries.tryEmit(weekSummaries(fixture.lastWeek, 4))
        runCurrent()

        val historyQueries = fixture.historyQueries
        val summaryQueries = fixture.summaryQueries
        fixture.viewModel.selectDate(nextDate)
        assertNewWeek(fixture.viewModel.uiState.value, nextDate, count = 4)
        runCurrent()
        assertEquals(historyQueries, fixture.historyQueries)
        assertEquals(summaryQueries, fixture.summaryQueries)
        assertEquals(1, pending.history.subscriptionCount.value)
        assertEquals(1, pending.summaries.subscriptionCount.value)
    }
}

private class WeekTransitionFixture(scope: CoroutineScope, timeSource: TimeSource) {
    val today = MissionDate.parse("2026-09-28")
    val lastWeek = today.plusDays(-7)
    var historyQueries = 0
    var summaryQueries = 0
    var historyStream: Flow<List<AlarmUsageRecord>> = flowOf(listOf(record(today)))
    var summaryStream: Flow<Map<MissionDate, AlarmActivitySummary>> = flowOf(weekSummaries(today, 5))
    private val historyRepository = object : MissionHistoryRepository {
        override suspend fun getHistory(month: MissionYearMonth) = error("Not used")
        override suspend fun record(entry: MissionHistoryEntry) = error("Not used")
        override fun observeLocalRecords(month: MissionYearMonth) = historyStream.also { historyQueries++ }
    }
    private val activityRepository = object : AlarmActivityRepository {
        override fun observeSummaries(dates: List<MissionDate>) = summaryStream
            .also { summaryQueries++ }
            .map { summaries -> dates.associateWith { summaries[it] ?: AlarmActivitySummary() } }
    }
    val viewModel = RecordsViewModel(
        GetRecordsHistory(historyRepository), today, activityRepository,
        coroutineScope = scope, timeSource = timeSource,
    )

    fun prepareResponse() = PendingWeekResponse().also {
        historyStream = it.history
        summaryStream = it.summaries
    }
}

private class PendingWeekResponse {
    val history = MutableSharedFlow<List<AlarmUsageRecord>>(replay = 1)
    val summaries = MutableSharedFlow<Map<MissionDate, AlarmActivitySummary>>(replay = 1)

    fun complete(date: MissionDate, count: Int = 2) {
        history.tryEmit(listOf(record(date)))
        summaries.tryEmit(weekSummaries(date, count))
    }
}

private fun weekSummaries(date: MissionDate, count: Int) =
    date.weekDates().associateWith { AlarmActivitySummary(ringingCount = count) }

private fun record(date: MissionDate) = AlarmUsageRecord(
    key = date.iso8601, date = date, alarmId = "alarm", ringingStartedAtEpochMillis = 1_000,
)

private fun assertNewWeek(state: RecordsUiState, date: MissionDate, count: Int = 2) {
    assertEquals(date, state.selectedDate)
    assertEquals(date.weekDates(), state.weekDays.map { it.date })
    assertEquals(date, state.weekDays.single { it.isSelected }.date)
    assertEquals(date, state.records.single().entries.single().date)
    assertEquals(count, state.activitySummary.ringingCount)
    assertFalse(state.isWeekChanging)
    assertFalse(state.showWeekLoadingIndicator)
    assertFalse(state.isLoading)
    assertFalse(state.isSummaryLoading)
    assertNull(state.weekChangeErrorMessage)
}
