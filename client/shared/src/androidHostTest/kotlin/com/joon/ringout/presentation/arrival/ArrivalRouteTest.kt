package com.joon.ringout.presentation.arrival

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.arrival.ArrivalGrowth
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.launchWithFontScale
import com.joon.ringout.presentation.navigation.LocalNavigationBackActionRegistrar
import com.joon.ringout.presentation.navigation.RegisterNavigationBackAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
class ArrivalRouteTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `뒤로 가기와 확인 버튼은 같은 저장을 수행하고 저장 성공 후에만 이동한다`() {
        val pending = CompletableDeferred<Unit>()
        val repository = RouteArrivalRepository().apply { acknowledge = { pending.await() } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = ArrivalViewModel("arrival-route", repository, scope) { 2_000 }
        var onBack: (() -> Unit)? = null
        val registrar: RegisterNavigationBackAction = { action ->
            onBack = action
            val unregister: () -> Unit = { if (onBack === action) onBack = null }
            unregister
        }
        var confirmed = 0
        var theme by mutableStateOf(ThemeMode.Dark)
        try {
            launchWithFontScale(1f) {
                CompositionLocalProvider(LocalNavigationBackActionRegistrar provides registrar) {
                    RingoutTheme(theme) { ArrivalRoute(viewModel, onConfirmed = { confirmed++ }) }
                }
            }.use {
                compose.onNodeWithText("공유하기").assertDoesNotExist()
                compose.onNodeWithText("오늘 하루 시작하기!").assertIsDisplayed()
                compose.runOnIdle { assertNotNull(onBack).invoke() }
                compose.onNodeWithText("확인 중…").assertIsDisplayed().assertIsNotEnabled()
                compose.runOnIdle {
                    assertNotNull(onBack).invoke()
                    assertEquals(listOf("arrival-route" to 2_000L), repository.confirmations)
                    assertEquals(0, confirmed)
                    pending.complete(Unit)
                }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertEquals(1, confirmed)
                    theme = ThemeMode.Light
                }
                compose.waitForIdle()
                compose.runOnIdle { assertEquals(1, confirmed) }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `확인 저장에 실패하면 화면을 유지하고 버튼으로 다시 저장한 뒤 이동한다`() {
        val repository = RouteArrivalRepository().apply { acknowledge = { error("확인 저장 실패") } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = ArrivalViewModel("arrival-route", repository, scope)
        var confirmed = 0
        val registrar: RegisterNavigationBackAction = { { } }
        try {
            launchWithFontScale(1f) {
                CompositionLocalProvider(LocalNavigationBackActionRegistrar provides registrar) {
                    ArrivalRoute(viewModel, onConfirmed = { confirmed++ })
                }
            }.use {
                compose.onNodeWithText("오늘 하루 시작하기!").performClick()
                compose.onNodeWithText("도착 확인을 저장하지 못했어요. 다시 시도해 주세요.").assertIsDisplayed()
                compose.onNodeWithText("목적지에 도착했어요!").assertIsDisplayed()
                compose.runOnIdle {
                    assertEquals(0, confirmed)
                    repository.acknowledge = {}
                }
                compose.onNodeWithText("오늘 하루 시작하기!").performClick()
                compose.waitForIdle()
                compose.runOnIdle {
                    assertEquals(1, confirmed)
                    assertEquals(2, repository.confirmations.size)
                }
            }
        } finally {
            scope.cancel()
        }
    }
}

private class RouteArrivalRepository : ArrivalResultRepository {
    var acknowledge: suspend () -> Unit = {}
    val confirmations = mutableListOf<Pair<String, Long>>()

    override suspend fun getResult(occurrenceId: String) = ArrivalResult(
        occurrenceId = occurrenceId,
        completedLocalDate = MissionDate.parse("2026-11-09"),
        completedAtEpochMillis = 1_000,
        alarmDismissedAtEpochMillis = 500,
        successfulDayCountAtArrival = 4,
        grewOnThisArrival = true,
        acknowledgedAtEpochMillis = null,
    )

    override suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long) {
        confirmations += occurrenceId to acknowledgedAtEpochMillis
        acknowledge()
    }

    override fun observeLatestUnacknowledgedResult(): Flow<ArrivalResult?> = error("unused")
    override suspend fun getGrowth(): ArrivalGrowth = error("unused")
    override fun observeGrowth(): Flow<ArrivalGrowth> = error("unused")
}
