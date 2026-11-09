package com.joon.ringout.presentation.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.joon.ringout.domain.arrival.ArrivalGrowth
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.FontScaleTestContent
import com.joon.ringout.presentation.arrival.PendingArrivalViewModel
import com.joon.ringout.presentation.launchWithFontScale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
class ArrivalNavigationIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `미션 종료 뒤 저장된 결과로 자동 진입하고 확인 저장이 끝나면 홈만 남긴다`() {
        val repository = ArrivalIntegrationRepository()
        val pendingConfirmation = CompletableDeferred<Unit>()
        repository.confirmationGate = pendingConfirmation
        val fixture = Fixture(repository)
        launch(fixture).use {
            compose.onNodeWithTag("arrival-test-home").assertIsDisplayed()
            compose.runOnIdle { fixture.activeOccurrenceId = "arrival-a" }
            compose.onNodeWithTag("arrival-test-active").assertIsDisplayed()

            // Both platforms can clear runtime state before the receipt transaction finishes.
            compose.runOnIdle { fixture.activeOccurrenceId = null }
            compose.onNodeWithTag("arrival-test-home").assertIsDisplayed()
            compose.onNodeWithText("목적지에 도착했어요!").assertDoesNotExist()

            compose.runOnIdle { repository.publish(arrival("arrival-a", successfulDays = 4)) }
            compose.onNodeWithText("목적지에 도착했어요!").assertIsDisplayed()
            compose.onNodeWithText("4번째").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf(AppRoute.Home, AppRoute.Arrival("arrival-a")), fixture.navigation.backStack)
            }
            compose.onNodeWithText("오늘 하루 시작하기!").performClick()
            compose.onNodeWithText("확인 중…").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf("arrival-a"), repository.confirmationIds)
                assertEquals(AppRoute.Arrival("arrival-a"), fixture.navigation.requestedRoute)
                pendingConfirmation.complete(Unit)
            }

            compose.onNodeWithTag("arrival-test-home").assertIsDisplayed()
            compose.onNodeWithText("목적지에 도착했어요!").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(listOf(AppRoute.Home), fixture.navigation.backStack)
                assertNotNull(repository.records.getValue("arrival-a").acknowledgedAtEpochMillis)
                repository.refreshPending()
            }
            compose.onNodeWithTag("arrival-test-home").assertIsDisplayed()
            compose.runOnIdle { assertEquals(1, repository.confirmationIds.size) }
        }
    }

    @Test
    fun `재시작 때 최신 결과를 표시하고 뒤로 확인하면 이전 결과도 소비하여 다시 나타나지 않는다`() {
        val repository = ArrivalIntegrationRepository().apply {
            publish(arrival("arrival-a", successfulDays = 4))
        }
        val fixture = Fixture(repository)
        launch(fixture).use {
            compose.onNodeWithText("4번째").assertIsDisplayed()
            compose.runOnIdle { repository.publish(arrival("arrival-b", successfulDays = 5)) }
            compose.onNodeWithText("5번째").assertIsDisplayed()
            compose.onNodeWithText("씨앗이 모두 자라\n나무가 되었어요!").assertIsDisplayed()
            compose.onNodeWithText("4번째").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(listOf(AppRoute.Home, AppRoute.Arrival("arrival-b")), fixture.navigation.backStack)
                fixture.input.start()
            }
            compose.waitForIdle()
            compose.runOnIdle { fixture.input.complete() }
            compose.onNodeWithTag("arrival-test-home").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf("arrival-b"), repository.confirmationIds)
                assertEquals(0, fixture.fallbackBackCalls)
                assertNotNull(repository.records.getValue("arrival-a").acknowledgedAtEpochMillis)
                assertNotNull(repository.records.getValue("arrival-b").acknowledgedAtEpochMillis)
            }
        }

        // A fresh Activity, observer, navigation state and ViewModel stores read the same storage.
        val restarted = Fixture(repository)
        launch(restarted).use {
            compose.onNodeWithTag("arrival-test-home").assertIsDisplayed()
            compose.onNodeWithText("목적지에 도착했어요!").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(listOf(AppRoute.Home), restarted.navigation.backStack)
                assertEquals(listOf("arrival-b"), repository.confirmationIds)
            }
        }
    }

    private fun launch(fixture: Fixture): FontScaleTestContent = launchWithFontScale(1f) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides fixture) {
            DisposableEffect(fixture) {
                fixture.navigationEventDispatcher.addInput(fixture.input)
                onDispose { fixture.navigationEventDispatcher.dispose() }
            }
            val observer = viewModel { PendingArrivalViewModel(fixture.repository) }
            val pending by observer.uiState.collectAsStateWithLifecycle()
            ActiveMissionNavigationEffect(
                activeMissionOccurrenceId = fixture.activeOccurrenceId,
                navigationState = fixture.navigation,
                pendingArrivalOccurrenceId = pending.result?.occurrenceId,
                isArrivalStateLoaded = pending.isLoaded,
            )
            RingoutNavHost(
                navigationState = fixture.navigation,
                displayedRoute = fixture.navigation.requestedRoute,
                viewModelStoreProvider = rememberViewModelStoreProvider(),
                backTransitions = NavigationBackTransitions(
                    enabled = false,
                    pop = { noNavigationTransition() },
                    predictivePop = { noNavigationTransition() },
                ),
                onBack = { route ->
                    fixture.fallbackBackCalls++
                    fixture.navigation.popBackStack(route)
                },
                graph = {
                    entry(AppRoute.Home) { Text("홈", Modifier.testTag("arrival-test-home")) }
                    entry<AppRoute.ActiveAlarmTracking> {
                        Text("진행 중인 미션", Modifier.testTag("arrival-test-active"))
                    }
                    arrivalGraph(
                        navigationState = fixture.navigation,
                        repository = fixture.repository,
                        pendingArrival = pending,
                        hasActiveRuntime = fixture.activeOccurrenceId != null,
                    )
                },
            )
        }
    }

    private class Fixture(val repository: ArrivalIntegrationRepository) : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
        val input = BackInput()
        val navigation = AppNavigationState()
        var activeOccurrenceId by mutableStateOf<String?>(null)
        var fallbackBackCalls = 0
    }

    private class BackInput : NavigationEventInput() {
        fun start() = dispatchOnBackStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
        fun complete() = dispatchOnBackCompleted()
    }
}

private class ArrivalIntegrationRepository : ArrivalResultRepository {
    val records = linkedMapOf<String, ArrivalResult>()
    private val pending = MutableStateFlow<ArrivalResult?>(null)
    val confirmationIds = mutableListOf<String>()
    var confirmationGate: CompletableDeferred<Unit>? = null

    fun publish(result: ArrivalResult) {
        records[result.occurrenceId] = result
        refreshPending()
    }

    fun refreshPending() {
        pending.value = records.values.filter { it.acknowledgedAtEpochMillis == null }
            .maxByOrNull { it.completedAtEpochMillis }
    }

    override suspend fun getResult(occurrenceId: String): ArrivalResult? = records[occurrenceId]
    override fun observeLatestUnacknowledgedResult(): Flow<ArrivalResult?> = pending

    override suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long) {
        confirmationIds += occurrenceId
        confirmationGate?.await()
        val completedThrough = records.getValue(occurrenceId).completedAtEpochMillis
        records.replaceAll { _, record ->
            if (record.completedAtEpochMillis <= completedThrough && record.acknowledgedAtEpochMillis == null) {
                record.copy(acknowledgedAtEpochMillis = acknowledgedAtEpochMillis)
            } else record
        }
        refreshPending()
    }

    override suspend fun getGrowth() = ArrivalGrowth(records.values.maxOfOrNull { it.successfulDayCountAtArrival } ?: 0)
    override fun observeGrowth(): Flow<ArrivalGrowth> = flowOf(ArrivalGrowth(0))
}

private fun arrival(occurrenceId: String, successfulDays: Int) = ArrivalResult(
    occurrenceId = occurrenceId,
    completedLocalDate = MissionDate.parse("2026-11-09"),
    completedAtEpochMillis = successfulDays * 1_000L,
    alarmDismissedAtEpochMillis = 500,
    successfulDayCountAtArrival = successfulDays,
    grewOnThisArrival = true,
    acknowledgedAtEpochMillis = null,
)
