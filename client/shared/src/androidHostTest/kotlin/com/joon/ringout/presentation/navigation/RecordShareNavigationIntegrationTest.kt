package com.joon.ringout.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import com.joon.ringout.domain.alarmactivity.AlarmActivityRepository
import com.joon.ringout.domain.alarmactivity.AlarmActivitySummary
import com.joon.ringout.domain.arrival.ArrivalGrowth
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.GetRecordsHistory
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.presentation.arrival.PendingArrivalUiState
import com.joon.ringout.presentation.launchWithFontScale
import com.joon.ringout.presentation.records.RecordsDetailRoute
import com.joon.ringout.presentation.records.RecordsRoute
import com.joon.ringout.presentation.records.RecordsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordShareNavigationIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `기록에서 공유를 열고 돌아오면 상세와 선택한 날짜가 유지된다`() {
        val selectedDate = MissionDate.of(2026, 9, 28)
        val today = MissionDate.of(2026, 9, 29)
        val records = listOf(
            AlarmUsageRecord("success", selectedDate, "success", MissionResult.SUCCESS,
                ringingStartedAtEpochMillis = 1_790_500_000_000, missionCompletedAtEpochMillis = 1_790_500_120_000,
                alarmId = "first"),
            AlarmUsageRecord("missing", selectedDate, "missing", MissionResult.SUCCESS,
                ringingStartedAtEpochMillis = 1_790_501_000_000, alarmId = "second"),
            AlarmUsageRecord("failure", selectedDate, "failure", MissionResult.FAILURE,
                ringingStartedAtEpochMillis = 1_790_502_000_000, missionCompletedAtEpochMillis = 1_790_502_120_000,
                alarmId = "third"),
        )
        val history = IntegrationRecordRepository(records)
        val arrival = IntegrationArrivalRepository()
        val recordsViewModel = RecordsViewModel(GetRecordsHistory(history), today, IntegrationActivityRepository())
        val navigation = AppNavigationState().apply { navigate(AppRoute.Records) }

        launchWithFontScale(1f) {
            ActiveMissionNavigationEffect(null, navigation, pendingArrivalOccurrenceId = null)
            val stores = rememberViewModelStoreProvider()
            RingoutNavHost(
                navigationState = navigation,
                displayedRoute = navigation.requestedRoute,
                viewModelStoreProvider = stores,
                backTransitions = NavigationBackTransitions(
                    enabled = false,
                    pop = { noNavigationTransition() },
                    predictivePop = { noNavigationTransition() },
                ),
                graph = {
                    entry(AppRoute.Home) { Text("홈") }
                    entry<AppRoute.Records>(clazzContentKey = AppRoute::viewModelStoreKey) {
                        RecordsRoute(recordsViewModel, onRecordClick = { navigation.navigate(AppRoute.RecordsDetail(it.iso8601)) })
                    }
                    entry<AppRoute.RecordsDetail>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
                        RecordsDetailRoute(
                            recordsViewModel, MissionDate.parse(route.date),
                            onBack = { navigation.popBackStack(route) },
                            onShare = { record ->
                                navigation.navigate(AppRoute.RecordShare(record.date.iso8601, record.groupKey,
                                    record.successRecordKey, record.occurrenceId))
                            },
                        )
                    }
                    arrivalGraph(navigation, arrival, history, PendingArrivalUiState(isLoaded = true), false, stores)
                },
            )
        }.use {
            val dateSelector = hasContentDescription("2026년 9월 28일", substring = true)
            compose.onNode(dateSelector).performClick().assertIsSelected()
            val card = SemanticsMatcher("알람 기록 자세히 보기") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == "알람 기록 자세히 보기"
            }
            compose.onAllNodes(card)[0].performClick()
            compose.onNodeWithText("2026. 09. 28").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("알람 기록 공유하기").assertCountEquals(1)
            compose.onNodeWithContentDescription("알람 기록 공유하기").performClick()
            compose.onNodeWithTag("arrival-share-card").assertIsDisplayed()
            compose.onNodeWithText("기상 시간").assertIsDisplayed()
            compose.onNodeWithText("목적지 도착").assertDoesNotExist()
            compose.runOnIdle {
                val share = navigation.requestedRoute as AppRoute.RecordShare
                assertEquals("success", share.successRecordKey)
                assertEquals("success", share.occurrenceId)
            }
            compose.onNodeWithContentDescription("뒤로가기").performClick()
            compose.onNodeWithText("2026. 09. 28").assertIsDisplayed()
            compose.onNodeWithContentDescription("뒤로가기").performClick()
            compose.onNode(dateSelector).assertIsSelected()
            compose.runOnIdle {
                assertEquals(AppRoute.Records, navigation.requestedRoute)
                assertEquals(selectedDate, recordsViewModel.uiState.value.selectedDate)
            }
        }
    }
}

private class IntegrationRecordRepository(private val records: List<AlarmUsageRecord>) : MissionHistoryRepository {
    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> = error("Not used")
    override suspend fun record(entry: MissionHistoryEntry): Boolean = error("Not used")
    override suspend fun getLocalRecords(month: MissionYearMonth): List<AlarmUsageRecord> =
        records.filter { it.date.belongsTo(month) }
    override fun observeLocalRecords(month: MissionYearMonth): Flow<List<AlarmUsageRecord>> =
        flowOf(records.filter { it.date.belongsTo(month) })
}

private class IntegrationActivityRepository : AlarmActivityRepository {
    override fun observeSummaries(dates: List<MissionDate>): Flow<Map<MissionDate, AlarmActivitySummary>> =
        flowOf(dates.associateWith { AlarmActivitySummary(ringingCount = 0) })
}

private class IntegrationArrivalRepository : ArrivalResultRepository {
    override suspend fun getResult(occurrenceId: String): ArrivalResult? = null
    override fun observeLatestUnacknowledgedResult(): Flow<ArrivalResult?> = flowOf(null)
    override suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long) = Unit
    override suspend fun getGrowth(): ArrivalGrowth = ArrivalGrowth(0)
    override fun observeGrowth(): Flow<ArrivalGrowth> = flowOf(ArrivalGrowth(0))
}
