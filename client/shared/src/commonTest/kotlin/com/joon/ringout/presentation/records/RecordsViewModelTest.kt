package com.joon.ringout.presentation.records

import com.joon.ringout.domain.alarmactivity.AlarmActivitySummary
import com.joon.ringout.domain.alarmactivity.AlarmActivityRepository

import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.toAlarmUsageRecord
import com.joon.ringout.domain.missionhistory.GetRecordsHistory
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModelTest {
    @Test
    fun `재울림이 저장되면 기존 카드에 행을 추가하고 도착 결과도 같은 행에 반영한다`() = runTest {
        val first = AlarmUsageRecord(
            key = "one", date = MissionDate.parse("2026-09-28"), alarmId = "alarm",
            ringingStartedAtEpochMillis = 1_000, ringingStoppedAtEpochMillis = 2_000,
        )
        val records = kotlinx.coroutines.flow.MutableStateFlow(listOf(first))
        val repository = object : MissionHistoryRepository {
            override suspend fun getHistory(month: MissionYearMonth) = emptyList<MissionHistoryEntry>()
            override fun observeLocalRecords(month: MissionYearMonth) = records
            override suspend fun record(entry: MissionHistoryEntry) = error("Not used")
        }
        val viewModel = recordsViewModel(repository, backgroundScope)
        viewModel.refresh()
        runCurrent()
        val cardKey = viewModel.uiState.value.records.single().key

        val retry = first.copy(key = "retry", ringingStartedAtEpochMillis = 3_000, ringingStoppedAtEpochMillis = null)
        records.value = listOf(first, retry)
        runCurrent()

        val card = viewModel.uiState.value.records.single()
        assertEquals(cardKey, card.key)
        assertEquals(listOf(first, retry), card.entries)
        assertEquals(2, viewModel.uiState.value.weekDays.single { it.isSelected }.recordCount)

        val arrived = retry.copy(ringingStoppedAtEpochMillis = 4_000, result = MissionResult.SUCCESS, missionCompletedAtEpochMillis = 5_000)
        records.value = listOf(first, arrived)
        runCurrent()

        assertEquals(cardKey, viewModel.uiState.value.records.single().key)
        assertEquals(listOf(first, arrived), viewModel.uiState.value.records.single().entries)
        assertEquals(MissionResult.SUCCESS, viewModel.uiState.value.weekDays.single { it.isSelected }.result)
    }

    @Test
    fun `도착 전 울림 카드가 나타나고 도착하면 같은 카드에 완료 결과를 반영한다`() = runTest {
        val ringing = AlarmUsageRecord(
            key = "occurrence:one", date = MissionDate.parse("2026-09-28"), occurrenceId = "one",
            ringingStartedAtEpochMillis = 1_000, ringingStoppedAtEpochMillis = 2_000,
        )
        val records = kotlinx.coroutines.flow.MutableStateFlow(listOf(ringing))
        val repository = object : MissionHistoryRepository {
            override suspend fun getHistory(month: MissionYearMonth) = emptyList<MissionHistoryEntry>()
            override fun observeLocalRecords(month: MissionYearMonth) = records
            override suspend fun record(entry: MissionHistoryEntry) = error("Not used")
        }
        val viewModel = recordsViewModel(repository, backgroundScope)
        viewModel.refresh()
        runCurrent()

        assertEquals(ringing, viewModel.uiState.value.records.single().entries.single())
        val cardKey = viewModel.uiState.value.records.single().key
        assertNull(viewModel.uiState.value.weekDays.single { it.isSelected }.result)

        val arrived = ringing.copy(result = MissionResult.SUCCESS, missionCompletedAtEpochMillis = 3_000)
        records.value = listOf(arrived)
        runCurrent()

        assertEquals(arrived, viewModel.uiState.value.records.single().entries.single())
        assertEquals(cardKey, viewModel.uiState.value.records.single().key)
        assertEquals(MissionResult.SUCCESS, viewModel.uiState.value.weekDays.single { it.isSelected }.result)
    }

    @Test
    fun `화면에 있는 기록에 종료 시각이 추가되면 새로고침 없이 갱신한다`() = runTest {
        val original = entry("2026-09-28", occurrenceId = "one")
        val history = kotlinx.coroutines.flow.MutableStateFlow(listOf(original))
        val repository = object : MissionHistoryRepository {
            override suspend fun getHistory(month: MissionYearMonth) = history.value
            override fun observeLocalHistory(month: MissionYearMonth) = history
            override suspend fun record(entry: MissionHistoryEntry) = error("Not used")
        }
        val viewModel = recordsViewModel(repository, backgroundScope)
        viewModel.refresh()
        runCurrent()
        assertNull(viewModel.uiState.value.records.single().entries.single().ringingStoppedAtEpochMillis)

        history.value = listOf(original.copy(ringingStoppedAtEpochMillis = 2_000))
        runCurrent()

        assertEquals(2_000L, viewModel.uiState.value.records.single().entries.single().ringingStoppedAtEpochMillis)
    }

    @Test
    fun `미션 완료 기록이 없어도 울림 집계는 실시간으로 갱신된다`() = runTest {
        val summaries = kotlinx.coroutines.flow.MutableStateFlow(
            AlarmActivitySummary(ringingCount = 0),
        )
        val activityRepository = object : AlarmActivityRepository {
            override fun observeSummary(date: MissionDate) = summaries
        }
        val viewModel = RecordsViewModel(
            GetRecordsHistory(RecordsRepository()),
            MissionDate.parse("2026-09-28"),
            activityRepository,
            coroutineScope = backgroundScope,
        )
        viewModel.refresh()
        runCurrent()

        summaries.value = AlarmActivitySummary(ringingCount = 5)
        runCurrent()

        assertEquals(5, viewModel.uiState.value.activitySummary.ringingCount)
        assertTrue(viewModel.uiState.value.records.isEmpty())
        assertFalse(viewModel.uiState.value.isSummaryLoading)
    }

    @Test
    fun `날짜를 바꾸면 이전 날짜의 집계 구독을 해제한다`() = runTest {
        val today = MissionDate.parse("2026-09-28")
        val yesterday = MissionDate.parse("2026-09-27")
        val streams = mapOf(
            today to kotlinx.coroutines.flow.MutableStateFlow(AlarmActivitySummary(ringingCount = 5)),
            yesterday to kotlinx.coroutines.flow.MutableStateFlow(AlarmActivitySummary(ringingCount = 1)),
        )
        val activityRepository = object : AlarmActivityRepository {
            override fun observeSummary(date: MissionDate) = streams.getValue(date)
        }
        val viewModel = RecordsViewModel(GetRecordsHistory(RecordsRepository()), today, activityRepository, coroutineScope = backgroundScope)
        viewModel.refresh()
        runCurrent()
        viewModel.selectDate(yesterday)
        assertTrue(viewModel.uiState.value.isSummaryLoading)
        assertNull(viewModel.uiState.value.activitySummary.ringingCount)
        runCurrent()
        streams.getValue(today).value = AlarmActivitySummary(ringingCount = 9)
        runCurrent()

        assertEquals(1, viewModel.uiState.value.activitySummary.ringingCount)
        assertEquals(0, streams.getValue(today).subscriptionCount.value)
    }

    @Test
    fun `집계 조회 실패를 재시도해도 미션 기록은 유지한다`() = runTest {
        var shouldFail = true
        val activityRepository = object : AlarmActivityRepository {
            override fun observeSummary(date: MissionDate) = kotlinx.coroutines.flow.flow {
                if (shouldFail) error("storage unavailable")
                emit(AlarmActivitySummary(ringingCount = 2))
            }
        }
        val viewModel = RecordsViewModel(
            GetRecordsHistory(RecordsRepository(listOf(entry("2026-09-28")))),
            MissionDate.parse("2026-09-28"), activityRepository, coroutineScope = backgroundScope,
        )
        viewModel.refresh()
        runCurrent()
        assertNotNull(viewModel.uiState.value.summaryErrorMessage)
        assertEquals(1, viewModel.uiState.value.records.size)
        shouldFail = false
        viewModel.retry()
        runCurrent()
        assertNull(viewModel.uiState.value.summaryErrorMessage)
        assertEquals(2, viewModel.uiState.value.activitySummary.ringingCount)
    }

    @Test
    fun `선택 날짜의 모든 기록을 최신순으로 보여주고 마지막 결과로 날짜를 표시한다`() = runTest {
        val success = entry("2026-09-28", MissionResult.SUCCESS)
        val failure = entry("2026-09-28", MissionResult.FAILURE)
        val yesterday = entry("2026-09-27")
        val repository = RecordsRepository(listOf(yesterday, success, failure))
        val viewModel = recordsViewModel(repository, backgroundScope)

        viewModel.refresh()
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(failure.toAlarmUsageRecord(2), success.toAlarmUsageRecord(1)), state.records.flatMap { it.entries })
        assertEquals(MissionResult.FAILURE, state.weekDays.single { it.isSelected }.result)
        assertEquals(2, state.weekDays.single { it.isSelected }.recordCount)

        viewModel.selectDate(MissionDate.parse("2026-09-27"))

        assertEquals(listOf(yesterday.toAlarmUsageRecord()), viewModel.uiState.value.records.flatMap { it.entries })
        assertEquals(1, repository.queries.size)
    }

    @Test
    fun `미래 날짜는 선택할 수 있지만 저장된 미래 기록과 표시는 제외한다`() = runTest {
        val repository = RecordsRepository(listOf(entry("2026-09-29")))
        val viewModel = recordsViewModel(repository, backgroundScope)
        viewModel.refresh()
        runCurrent()

        viewModel.selectDate(MissionDate.parse("2026-09-29"))

        val state = viewModel.uiState.value
        assertEquals(emptyList(), state.records)
        assertTrue(state.weekDays.single { it.isSelected }.isFuture)
        assertNull(state.weekDays.single { it.isSelected }.result)
        assertEquals(0, state.weekDays.single { it.isSelected }.recordCount)
    }

    @Test
    fun `일주일 이동 시 연도 경계를 넘어 같은 요일을 선택한다`() = runTest {
        val viewModel = recordsViewModel(
            RecordsRepository(),
            backgroundScope,
            initialDate = MissionDate.parse("2026-01-01"),
        )

        viewModel.previousWeek()
        runCurrent()
        assertEquals(MissionDate.parse("2025-12-25"), viewModel.uiState.value.selectedDate)

        viewModel.nextWeek()
        runCurrent()
        assertEquals(MissionDate.parse("2026-01-01"), viewModel.uiState.value.selectedDate)
        assertEquals(MissionDate.parse("2025-12-28"), viewModel.uiState.value.weekDays.first().date)
        assertEquals(MissionDate.parse("2026-01-03"), viewModel.uiState.value.weekDays.last().date)
    }

    @Test
    fun `조회 실패는 빈 기록과 구별하고 재시도 성공 시 오류를 해제한다`() = runTest {
        val repository = RecordsRepository(listOf(entry("2026-09-28")))
        repository.loader = { error("Database unavailable") }
        val viewModel = recordsViewModel(repository, backgroundScope)

        viewModel.refresh()
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.errorMessage)

        repository.loader = null
        viewModel.retry()
        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        runCurrent()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, viewModel.uiState.value.records.size)
    }

    @Test
    fun `이전 주의 늦은 응답은 현재 선택한 주의 결과를 덮어쓰지 않는다`() = runTest {
        val pending = PendingHistory()
        val selectedEntry = entry("2026-09-21", MissionResult.FAILURE)
        val repository = RecordsRepository().apply { loader = { pending.await() } }
        val viewModel = recordsViewModel(repository, backgroundScope)
        viewModel.refresh()
        runCurrent()

        repository.loader = { listOf(selectedEntry) }
        viewModel.previousWeek()
        runCurrent()
        pending.complete(listOf(entry("2026-09-28")))
        runCurrent()

        assertEquals(MissionDate.parse("2026-09-21"), viewModel.uiState.value.selectedDate)
        assertEquals(listOf(selectedEntry.toAlarmUsageRecord()), viewModel.uiState.value.records.flatMap { it.entries })
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `취소된 주 조회의 늦은 오류는 최신 성공 상태를 오류로 바꾸지 않는다`() = runTest {
        val pending = PendingHistory()
        val repository = RecordsRepository().apply { loader = { pending.await() } }
        val viewModel = recordsViewModel(repository, backgroundScope)
        viewModel.refresh()
        runCurrent()

        repository.loader = { emptyList() }
        viewModel.previousWeek()
        runCurrent()
        pending.fail(IllegalStateException("Old database failure"))
        runCurrent()

        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(MissionDate.parse("2026-09-21"), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `월을 바꾼 뒤 도착한 이전 달 응답은 새 달의 표시를 덮어쓰지 않는다`() = runTest {
        val pending = PendingHistory()
        val repository = RecordsRepository().apply { loader = { pending.await() } }
        val viewModel = recordsViewModel(repository, backgroundScope)
        viewModel.openCalendar()
        runCurrent()

        repository.loader = { listOf(entry("2026-08-20", MissionResult.FAILURE)) }
        viewModel.previousMonth()
        runCurrent()
        pending.complete(listOf(entry("2026-09-28")))
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(MissionYearMonth(2026, 8), state.calendarMonth)
        assertTrue(state.calendarDays.filterNotNull().all { it.date.month == 8 })
        assertEquals(MissionResult.FAILURE, state.calendarDays.filterNotNull().single { it.date.day == 20 }.result)
        assertFalse(state.isCalendarLoading)
    }

    @Test
    fun `월 달력 오류와 재시도는 이미 불러온 선택 날짜 기록에 영향을 주지 않는다`() = runTest {
        val todayEntry = entry("2026-09-28")
        val repository = RecordsRepository(listOf(todayEntry))
        val viewModel = recordsViewModel(repository, backgroundScope)
        viewModel.refresh()
        runCurrent()
        repository.loader = { error("Calendar failed") }

        viewModel.openCalendar()
        runCurrent()

        assertNotNull(viewModel.uiState.value.calendarErrorMessage)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf(todayEntry.toAlarmUsageRecord()), viewModel.uiState.value.records.flatMap { it.entries })

        repository.loader = null
        viewModel.retryCalendar()
        runCurrent()

        assertNull(viewModel.uiState.value.calendarErrorMessage)
        assertFalse(viewModel.uiState.value.isCalendarLoading)
        assertEquals(MissionResult.SUCCESS, viewModel.uiState.value.calendarDays.filterNotNull().single { it.date.day == 28 }.result)
    }

    @Test
    fun `월 달력 날짜 선택은 해당 주로 이동하고 모달을 닫는다`() = runTest {
        val record = entry("2026-08-31")
        val repository = RecordsRepository(listOf(record))
        val viewModel = recordsViewModel(repository, backgroundScope)
        viewModel.openCalendar()
        runCurrent()

        viewModel.selectDate(record.completedAt)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isCalendarVisible)
        assertEquals(record.completedAt, state.selectedDate)
        assertEquals(MissionDate.parse("2026-08-30"), state.weekDays.first().date)
        assertEquals(MissionDate.parse("2026-09-05"), state.weekDays.last().date)
        assertEquals(listOf(record.toAlarmUsageRecord()), state.records.flatMap { it.entries })
    }

    @Test
    fun `다시 진입하면 추가된 로컬 기록과 현재 날짜를 갱신한다`() = runTest {
        var today = MissionDate.parse("2026-09-27")
        val repository = RecordsRepository()
        val viewModel = RecordsViewModel(
            getRecordsHistory = GetRecordsHistory(repository),
            activityRepository = EmptyActivityRepository,
            initialDate = today,
            currentDate = { today },
            coroutineScope = backgroundScope,
        )
        viewModel.refresh()
        runCurrent()
        repository.entries += entry("2026-09-27")
        today = MissionDate.parse("2026-09-28")

        viewModel.refresh()
        runCurrent()

        assertEquals(1, viewModel.uiState.value.records.size)
        assertEquals(today, viewModel.uiState.value.today)
        assertEquals(today, viewModel.uiState.value.weekDays.single { it.isToday }.date)
    }
}

private fun recordsViewModel(
    repository: MissionHistoryRepository,
    scope: CoroutineScope,
    initialDate: MissionDate = MissionDate.parse("2026-09-28"),
): RecordsViewModel = RecordsViewModel(
    getRecordsHistory = GetRecordsHistory(repository),
    activityRepository = EmptyActivityRepository,
    initialDate = initialDate,
    coroutineScope = scope,
)

private class RecordsRepository(
    initialEntries: List<MissionHistoryEntry> = emptyList(),
) : MissionHistoryRepository {
    val entries = initialEntries.toMutableList()
    val queries = mutableListOf<MissionYearMonth>()
    var loader: (suspend (MissionYearMonth) -> List<MissionHistoryEntry>)? = null

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> =
        error("Records must query local history.")

    override suspend fun getLocalHistory(month: MissionYearMonth): List<MissionHistoryEntry> {
        queries += month
        return loader?.invoke(month) ?: entries.filter { it.completedAt.belongsTo(month) }
    }

    override suspend fun record(entry: MissionHistoryEntry): Boolean = error("Not used")
}

/** Deliberately ignores job cancellation to exercise stale response guards. */
private class PendingHistory {
    private lateinit var continuation: Continuation<List<MissionHistoryEntry>>

    suspend fun await(): List<MissionHistoryEntry> = suspendCoroutine { continuation = it }

    fun complete(entries: List<MissionHistoryEntry>) = continuation.resume(entries)

    fun fail(error: Exception) = continuation.resumeWithException(error)
}

private fun entry(
    date: String,
    result: MissionResult = MissionResult.SUCCESS,
    occurrenceId: String? = null,
): MissionHistoryEntry = MissionHistoryEntry(result, MissionDate.parse(date), occurrenceId)

private object EmptyActivityRepository : AlarmActivityRepository {
    override fun observeSummary(date: MissionDate) = kotlinx.coroutines.flow.flowOf(
        AlarmActivitySummary(),
    )
}
