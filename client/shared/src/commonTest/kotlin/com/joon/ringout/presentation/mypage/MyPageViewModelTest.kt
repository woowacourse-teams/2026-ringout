package com.joon.ringout.presentation.mypage

import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.DestinationSelectionSource
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.StampMonthChangeDirection
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MyPageViewModelTest {
    @Test
    fun eachScreenEntryReloadsAndRecordsTheCalendarOnce() =
        withViewModel { viewModel, repository, analytics ->
            val firstEntry = MyPageEntryToken()
            viewModel.onScreenEntered(firstEntry, AnalyticsLoginState.LoggedOut)

            assertFalse(viewModel.uiState.isLoading)
            assertEquals(emptySet(), viewModel.uiState.successDates)
            assertEquals(
                listOf(CalendarViewed(August2026, AnalyticsLoginState.LoggedOut)),
                analytics.calendarViewed,
            )

            viewModel.onScreenEntered(firstEntry, AnalyticsLoginState.LoggedIn)

            assertEquals(listOf(August2026), repository.queries)
            assertEquals(1, analytics.calendarViewed.size)

            val successDate = MissionDate.parse("2026-08-05")
            repository.entries += MissionHistoryEntry(
                result = MissionResult.SUCCESS,
                completedAt = successDate,
                occurrenceId = "occurrence-success",
            )
            viewModel.onScreenEntered(MyPageEntryToken(), AnalyticsLoginState.LoggedIn)

            assertEquals(setOf(successDate), viewModel.uiState.successDates)
            assertEquals(listOf(August2026, August2026), repository.queries)
            assertEquals(
                listOf(
                    CalendarViewed(August2026, AnalyticsLoginState.LoggedOut),
                    CalendarViewed(August2026, AnalyticsLoginState.LoggedIn),
                ),
                analytics.calendarViewed,
            )
        }

    @Test
    fun failedEntryRecordsCalendarViewedOnlyAfterTheFirstSuccessfulRetry() =
        withViewModel { viewModel, repository, analytics ->
            repository.failuresRemaining = 1

            viewModel.onScreenEntered(MyPageEntryToken(), AnalyticsLoginState.LoggedIn)

            assertNotNull(viewModel.uiState.errorMessage)
            assertEquals(emptyList(), analytics.calendarViewed)

            viewModel.retry()

            assertNull(viewModel.uiState.errorMessage)
            assertEquals(
                listOf(CalendarViewed(August2026, AnalyticsLoginState.LoggedIn)),
                analytics.calendarViewed,
            )

            viewModel.retry()

            assertEquals(1, analytics.calendarViewed.size)
            assertEquals(listOf(August2026, August2026, August2026), repository.queries)
        }

    @Test
    fun monthNavigationRecordsEveryTapAndDoesNotRecordAnotherCalendarView() = withViewModel(
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
    ) { viewModel, repository, analytics ->
        viewModel.onScreenEntered(MyPageEntryToken(), AnalyticsLoginState.LoggedOut)
        viewModel.onPreviousMonthClick(AnalyticsLoginState.LoggedOut)

        assertEquals(July2026, viewModel.uiState.selectedMonth.value)
        assertEquals(
            setOf(MissionDate.parse("2026-07-03")),
            viewModel.uiState.successDates,
        )

        viewModel.onNextMonthClick(AnalyticsLoginState.LoggedOut)

        assertEquals(August2026, viewModel.uiState.selectedMonth.value)
        assertEquals(
            setOf(MissionDate.parse("2026-08-05")),
            viewModel.uiState.successDates,
        )
        assertEquals(
            listOf(
                MonthChanged(
                    direction = StampMonthChangeDirection.Previous,
                    month = July2026,
                    loginState = AnalyticsLoginState.LoggedOut,
                ),
                MonthChanged(
                    direction = StampMonthChangeDirection.Next,
                    month = August2026,
                    loginState = AnalyticsLoginState.LoggedOut,
                ),
            ),
            analytics.monthChanged,
        )
        assertEquals(1, analytics.calendarViewed.size)
        assertEquals(listOf(August2026, July2026, August2026), repository.queries)
    }

    @Test
    fun monthNavigationHandlesTheDecemberJanuaryBoundary() = withViewModel(
        initialMonth = December2025,
    ) { viewModel, _, analytics ->
        viewModel.onScreenEntered(MyPageEntryToken(), AnalyticsLoginState.LoggedIn)

        viewModel.onNextMonthClick(AnalyticsLoginState.LoggedIn)
        viewModel.onPreviousMonthClick(AnalyticsLoginState.LoggedIn)

        assertEquals(
            listOf(
                MonthChanged(
                    direction = StampMonthChangeDirection.Next,
                    month = January2026,
                    loginState = AnalyticsLoginState.LoggedIn,
                ),
                MonthChanged(
                    direction = StampMonthChangeDirection.Previous,
                    month = December2025,
                    loginState = AnalyticsLoginState.LoggedIn,
                ),
            ),
            analytics.monthChanged,
        )
    }

    @Test
    fun monthChangeIsRecordedEvenWhenTheRepositoryRequestFails() =
        withViewModel { viewModel, repository, analytics ->
            viewModel.onScreenEntered(MyPageEntryToken(), AnalyticsLoginState.LoggedIn)
            repository.failuresRemaining = 1

            viewModel.onNextMonthClick(AnalyticsLoginState.LoggedIn)

            assertEquals(
                listOf(
                    MonthChanged(
                        direction = StampMonthChangeDirection.Next,
                        month = September2026,
                        loginState = AnalyticsLoginState.LoggedIn,
                    ),
                ),
                analytics.monthChanged,
            )
            assertNotNull(viewModel.uiState.errorMessage)
            assertEquals(1, analytics.calendarViewed.size)
        }

    @Test
    fun monthEventsUseTheCurrentLoginStateWithoutChangingTheEntryViewState() =
        withViewModel { viewModel, _, analytics ->
            val entryToken = MyPageEntryToken()
            viewModel.onScreenEntered(entryToken, AnalyticsLoginState.LoggedIn)

            viewModel.onScreenEntered(entryToken, AnalyticsLoginState.LoggedOut)
            viewModel.onNextMonthClick(AnalyticsLoginState.LoggedOut)

            assertEquals(
                listOf(CalendarViewed(August2026, AnalyticsLoginState.LoggedIn)),
                analytics.calendarViewed,
            )
            assertEquals(
                listOf(
                    MonthChanged(
                        direction = StampMonthChangeDirection.Next,
                        month = September2026,
                        loginState = AnalyticsLoginState.LoggedOut,
                    ),
                ),
                analytics.monthChanged,
            )
        }
}

private inline fun withViewModel(
    entries: MutableList<MissionHistoryEntry> = mutableListOf(),
    initialMonth: MissionYearMonth = August2026,
    block: (
        MyPageViewModel,
        FakeMissionHistoryRepository,
        RecordingProductAnalyticsRecorder,
    ) -> Unit,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val repository = FakeMissionHistoryRepository(entries)
    val analytics = RecordingProductAnalyticsRecorder()
    val viewModel = MyPageViewModel(
        getMissionSuccessDates = GetMissionSuccessDates(repository),
        productAnalyticsRecorder = analytics,
        initialMonth = initialMonth,
        coroutineScope = scope,
    )
    try {
        block(viewModel, repository, analytics)
    } finally {
        scope.cancel()
    }
}

private class FakeMissionHistoryRepository(
    val entries: MutableList<MissionHistoryEntry>,
) : MissionHistoryRepository {
    val queries = mutableListOf<MissionYearMonth>()
    var failuresRemaining = 0

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> {
        queries += month
        if (failuresRemaining > 0) {
            failuresRemaining--
            error("mission history failed")
        }
        return entries.filter { entry -> entry.completedAt.belongsTo(month) }
    }

    override suspend fun record(entry: MissionHistoryEntry): Boolean {
        if (entries.any { it.occurrenceId == entry.occurrenceId }) return false
        entries += entry
        return true
    }
}

private class RecordingProductAnalyticsRecorder : ProductAnalyticsRecorder {
    val calendarViewed = mutableListOf<CalendarViewed>()
    val monthChanged = mutableListOf<MonthChanged>()

    override fun recordDestinationCreated(
        destinationId: Long,
        loginState: AnalyticsLoginState,
    ) = Unit

    override fun recordDestinationSelected(
        source: DestinationSelectionSource,
        loginState: AnalyticsLoginState,
    ) = Unit

    override fun recordStampCalendarViewed(
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    ) {
        calendarViewed += CalendarViewed(MissionYearMonth(year, month), loginState)
    }

    override fun recordStampMonthChanged(
        direction: StampMonthChangeDirection,
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    ) {
        monthChanged += MonthChanged(direction, MissionYearMonth(year, month), loginState)
    }

    override fun recordAccountWithdrawalCompleted() = Unit

    override fun recordLoginStarted(provider: AnalyticsAuthProvider) = Unit

    override fun recordLoginCompleted(
        provider: AnalyticsAuthProvider,
        isNewUser: Boolean,
    ) = Unit

    override fun recordSignupCompleted(provider: AnalyticsAuthProvider) = Unit
}

private data class CalendarViewed(
    val month: MissionYearMonth,
    val loginState: AnalyticsLoginState,
)

private data class MonthChanged(
    val direction: StampMonthChangeDirection,
    val month: MissionYearMonth,
    val loginState: AnalyticsLoginState,
)

private val July2026 = MissionYearMonth(2026, 7)
private val August2026 = MissionYearMonth(2026, 8)
private val September2026 = MissionYearMonth(2026, 9)
private val December2025 = MissionYearMonth(2025, 12)
private val January2026 = MissionYearMonth(2026, 1)
