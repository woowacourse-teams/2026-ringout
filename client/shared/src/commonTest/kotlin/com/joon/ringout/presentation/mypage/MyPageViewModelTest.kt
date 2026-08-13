package com.joon.ringout.presentation.mypage

import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MyPageViewModelTest {
    @Test
    fun reenteringMyPageReloadsTheSelectedMonth() = withViewModel { viewModel, repository ->
        viewModel.onScreenEntered()

        assertFalse(viewModel.uiState.isLoading)
        assertEquals(emptySet(), viewModel.uiState.successDates)

        val successDate = MissionDate.parse("2026-08-05")
        repository.entries += MissionHistoryEntry(
            result = MissionResult.SUCCESS,
            completedAt = successDate,
            occurrenceId = "occurrence-success",
        )

        viewModel.onScreenEntered()

        assertEquals(setOf(successDate), viewModel.uiState.successDates)
        assertEquals(listOf(August2026, August2026), repository.queries)
    }

    @Test
    fun monthNavigationLoadsEachSelectedMonthFromTheRepository() = withViewModel(
        entries = mutableListOf(
            MissionHistoryEntry(
                result = MissionResult.SUCCESS,
                completedAt = MissionDate.parse("2026-07-03"),
                occurrenceId = "july-success",
            ),
            MissionHistoryEntry(
                result = MissionResult.SUCCESS,
                completedAt = MissionDate.parse("2026-08-05"),
                occurrenceId = "august-success",
            ),
        ),
    ) { viewModel, repository ->
        viewModel.onScreenEntered()
        viewModel.onPreviousMonthClick()

        assertEquals(MissionYearMonth(2026, 7), viewModel.uiState.selectedMonth.value)
        assertEquals(
            setOf(MissionDate.parse("2026-07-03")),
            viewModel.uiState.successDates,
        )

        viewModel.onNextMonthClick()

        assertEquals(August2026, viewModel.uiState.selectedMonth.value)
        assertEquals(
            setOf(MissionDate.parse("2026-08-05")),
            viewModel.uiState.successDates,
        )
        assertEquals(
            listOf(August2026, MissionYearMonth(2026, 7), August2026),
            repository.queries,
        )
    }
}

private inline fun withViewModel(
    entries: MutableList<MissionHistoryEntry> = mutableListOf(),
    block: (MyPageViewModel, FakeMissionHistoryRepository) -> Unit,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val repository = FakeMissionHistoryRepository(entries)
    val viewModel = MyPageViewModel(
        getMissionSuccessDates = GetMissionSuccessDates(repository),
        initialMonth = August2026,
        coroutineScope = scope,
    )
    try {
        block(viewModel, repository)
    } finally {
        scope.cancel()
    }
}

private class FakeMissionHistoryRepository(
    val entries: MutableList<MissionHistoryEntry>,
) : MissionHistoryRepository {
    val queries = mutableListOf<MissionYearMonth>()

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> {
        queries += month
        return entries.filter { entry -> entry.completedAt.belongsTo(month) }
    }

    override suspend fun record(entry: MissionHistoryEntry): Boolean {
        if (entries.any { it.occurrenceId == entry.occurrenceId }) return false
        entries += entry
        return true
    }
}

private val August2026 = MissionYearMonth(2026, 8)
