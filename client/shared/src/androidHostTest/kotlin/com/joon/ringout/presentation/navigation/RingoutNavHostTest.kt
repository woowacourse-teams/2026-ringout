package com.joon.ringout.presentation.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.joon.ringout.presentation.FontScaleTestContent
import com.joon.ringout.presentation.launchWithFontScale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
class RingoutNavHostTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `뒤로가기 버튼을 누르면 퇴장 화면을 이동 중 유지하고 애니메이션이 끝나면 제거한다`() {
        val fixture = Fixture(AppRoute.AddAlarm)
        launch(fixture).use {
            compose.onNodeWithTag("draft-AddAlarm").assertIsDisplayed()
            withManualClock {
                compose.onNodeWithTag("back-AddAlarm").performClick()
                advanceTransition(160)

                compose.runOnIdle {
                    assertEquals(listOf(AppRoute.Home), fixture.navigation.backStack)
                    fixture.assertPartiallyMoved(AppRoute.AddAlarm)
                    assertEquals(0, fixture.disposalCount(AppRoute.AddAlarm))
                }

                advanceTransition(1_500)
                compose.runOnIdle { assertEquals(1, fixture.disposalCount(AppRoute.AddAlarm)) }
            }
            compose.onNodeWithTag("draft-Home").assertIsDisplayed()
        }
    }

    @Test
    fun `가장자리 제스처 진행률에 따라 화면이 움직이고 취소하면 입력값과 백스택이 유지된다`() {
        val fixture = Fixture(AppRoute.AddAlarm)
        launch(fixture).use {
            compose.onNodeWithTag("draft-AddAlarm").performTextReplacement("아침 운동 07:20")
            withManualClock {
                compose.runOnIdle { fixture.input.start() }
                advanceTransition(32)
                compose.runOnIdle { fixture.input.progress(0.5f) }
                advanceTransition(160)

                compose.runOnIdle {
                    fixture.assertPartiallyMoved(AppRoute.AddAlarm)
                    assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), fixture.navigation.backStack)
                    assertEquals(0, fixture.navigationBackCalls)
                }

                compose.runOnIdle { fixture.input.cancel() }
                advanceTransition(1_500)
                compose.runOnIdle {
                    assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), fixture.navigation.backStack)
                    assertTrue(abs(fixture.positions.getValue(AppRoute.AddAlarm)) < 1f)
                    assertEquals(0, fixture.disposalCount(AppRoute.AddAlarm))
                    assertEquals(0, fixture.navigationBackCalls)
                }
            }
            compose.onNodeWithTag("draft-AddAlarm").assertTextEquals("아침 운동 07:20")
        }
    }

    @Test
    fun `가장자리 제스처를 완료하면 이전 화면으로 한 단계만 돌아간다`() {
        val fixture = Fixture(AppRoute.Destination(1))
        launch(fixture).use {
            compose.onNodeWithTag("draft-Destination").assertIsDisplayed()
            withManualClock {
                compose.runOnIdle { fixture.input.start() }
                advanceTransition(32)
                compose.runOnIdle { fixture.input.progress(0.65f) }
                advanceTransition(160)
                compose.runOnIdle { fixture.input.complete() }
                advanceTransition(1_500)

                compose.runOnIdle {
                    assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), fixture.navigation.backStack)
                    assertEquals(1, fixture.navigationBackCalls)
                    assertEquals(1, fixture.disposalCount(AppRoute.Destination(1)))
                }
            }
            compose.onNodeWithTag("draft-AddAlarm").assertIsDisplayed()
        }
    }

    @Test
    fun `뒤로가기 차단 중에는 화면 미리보기와 백스택 제거가 모두 실행되지 않는다`() {
        val fixture = Fixture(AppRoute.AlarmSound).apply { isBackBlocked = true }
        launch(fixture).use {
            compose.onNodeWithTag("draft-AlarmSound").assertIsDisplayed()
            withManualClock {
                compose.runOnIdle { fixture.input.start() }
                advanceTransition(32)
                compose.runOnIdle { fixture.input.progress(0.8f) }
                advanceTransition(160)
                compose.runOnIdle {
                    assertTrue(abs(fixture.positions.getValue(AppRoute.AlarmSound)) < 1f)
                    assertEquals(0, fixture.predictiveTransitions)
                    fixture.input.complete()
                }
                advanceTransition(1_500)
                compose.runOnIdle {
                    assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm, AppRoute.AlarmSound), fixture.navigation.backStack)
                    assertEquals(0, fixture.navigationBackCalls)
                    assertEquals(0, fixture.disposalCount(AppRoute.AlarmSound))
                    fixture.isBackBlocked = false
                }
                advanceTransition(32)
                compose.runOnIdle { fixture.input.start() }
                advanceTransition(32)
                compose.runOnIdle { fixture.input.complete() }
                advanceTransition(1_500)
                compose.runOnIdle {
                    assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), fixture.navigation.backStack)
                    assertEquals(1, fixture.navigationBackCalls)
                }
            }
        }
    }

    @Test
    fun `제스처 도중 저장이 시작되면 최신 화면 콜백이 이동을 막고 저장 완료 후 다시 뒤로갈 수 있다`() {
        val fixture = Fixture(AppRoute.AddAlarm).apply { useEntryBackAction = true }
        launch(fixture).use {
            compose.onNodeWithTag("draft-AddAlarm").performTextReplacement("저장 중인 알람")
            withManualClock {
                compose.runOnIdle { fixture.input.start() }
                advanceTransition(32)
                compose.runOnIdle { fixture.input.progress(0.6f) }
                advanceTransition(160)
                compose.runOnIdle {
                    fixture.assertPartiallyMoved(AppRoute.AddAlarm)
                    fixture.isEntrySaving = true
                }
                advanceTransition(32)
                compose.runOnIdle { fixture.input.complete() }
                advanceTransition(1_500)

                compose.runOnIdle {
                    assertEquals(listOf(AppRoute.Home, AppRoute.AddAlarm), fixture.navigation.backStack)
                    assertEquals(0, fixture.entryBackCalls)
                    assertEquals(0, fixture.navigationBackCalls)
                    assertEquals(0, fixture.disposalCount(AppRoute.AddAlarm))
                    assertTrue(abs(fixture.positions.getValue(AppRoute.AddAlarm)) < 1f)
                }
                compose.onNodeWithTag("draft-AddAlarm").assertTextEquals("저장 중인 알람")

                compose.runOnIdle { fixture.isEntrySaving = false }
                advanceTransition(32)
                compose.runOnIdle { fixture.input.start() }
                advanceTransition(32)
                compose.runOnIdle { fixture.input.progress(0.6f) }
                advanceTransition(160)
                compose.runOnIdle { fixture.input.complete() }
                advanceTransition(1_500)

                compose.runOnIdle {
                    assertEquals(listOf(AppRoute.Home), fixture.navigation.backStack)
                    assertEquals(1, fixture.entryBackCalls)
                    assertEquals(0, fixture.navigationBackCalls)
                    assertEquals(1, fixture.disposalCount(AppRoute.AddAlarm))
                }
            }
            compose.onNodeWithTag("draft-Home").assertIsDisplayed()
        }
    }

    @Test
    fun `모임 탭에서 뒤로가기 버튼을 누르면 홈으로 애니메이션 없이 돌아간다`() {
        val fixture = Fixture(AppRoute.Social)
        launch(fixture).use {
            compose.onNodeWithTag("draft-Social").assertIsDisplayed()
            withManualClock {
                compose.onNodeWithTag("back-Social").performClick()
                advanceTransition(64)

                compose.runOnIdle {
                    assertEquals(listOf(AppRoute.Home), fixture.navigation.backStack)
                    assertEquals(1, fixture.disposalCount(AppRoute.Social))
                    assertEquals(0, fixture.popTransitions)
                    assertEquals(0, fixture.predictiveTransitions)
                    assertTrue(fixture.positionHistory.getValue(AppRoute.Social).all { abs(it) < 1f })
                    assertTrue(fixture.positionHistory.getValue(AppRoute.Home).all { abs(it) < 1f })
                }
            }
            compose.onNodeWithTag("draft-Home").assertIsDisplayed()
        }
    }

    @Test
    fun `모임 탭의 가장자리 제스처는 화면을 움직이지 않고 취소하거나 홈으로 한 단계 돌아간다`() {
        val fixture = Fixture(AppRoute.Social)
        launch(fixture).use {
            compose.onNodeWithTag("draft-Social").performTextReplacement("유지할 모임 상태")
            withManualClock {
                compose.runOnIdle { fixture.input.start() }
                advanceTransition(32)
                compose.runOnIdle { fixture.input.progress(0.6f) }
                advanceTransition(160)

                compose.runOnIdle {
                    assertEquals(listOf(AppRoute.Home, AppRoute.Social), fixture.navigation.backStack)
                    assertEquals(0, fixture.navigationBackCalls)
                    assertEquals(0, fixture.disposalCount(AppRoute.Social))
                    assertEquals(0, fixture.popTransitions)
                    assertEquals(0, fixture.predictiveTransitions)
                    assertTrue(fixture.positionHistory.getValue(AppRoute.Social).all { abs(it) < 1f })
                }

                compose.runOnIdle { fixture.input.cancel() }
                advanceTransition(1_500)
                compose.runOnIdle {
                    assertEquals(listOf(AppRoute.Home, AppRoute.Social), fixture.navigation.backStack)
                    assertEquals(0, fixture.navigationBackCalls)
                    assertEquals(0, fixture.disposalCount(AppRoute.Social))
                }
                compose.onNodeWithTag("draft-Social").assertTextEquals("유지할 모임 상태")

                compose.runOnIdle { fixture.input.start() }
                advanceTransition(32)
                compose.runOnIdle { fixture.input.progress(0.7f) }
                advanceTransition(160)
                compose.runOnIdle { fixture.input.complete() }
                advanceTransition(1_500)

                compose.runOnIdle {
                    assertEquals(listOf(AppRoute.Home), fixture.navigation.backStack)
                    assertEquals(1, fixture.navigationBackCalls)
                    assertEquals(1, fixture.disposalCount(AppRoute.Social))
                    assertEquals(0, fixture.popTransitions)
                    assertEquals(0, fixture.predictiveTransitions)
                    assertTrue(fixture.positionHistory.getValue(AppRoute.Social).all { abs(it) < 1f })
                    assertTrue(fixture.positionHistory.getValue(AppRoute.Home).all { abs(it) < 1f })
                }
            }
            compose.onNodeWithTag("draft-Home").assertIsDisplayed()
        }
    }

    @Test
    fun `하단 홈 탭으로 이동할 때는 뒤로가기 전환을 실행하지 않는다`() {
        val fixture = Fixture(AppRoute.MyPage)
        launch(fixture).use {
            compose.onNodeWithTag("draft-MyPage").assertIsDisplayed()
            compose.onNodeWithContentDescription("홈").performClick()
            compose.onNodeWithTag("draft-Home").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf(AppRoute.Home), fixture.navigation.backStack)
                assertEquals(0, fixture.popTransitions)
                assertEquals(0, fixture.predictiveTransitions)
                assertEquals(0, fixture.navigationBackCalls)
            }
        }
    }

    @Test
    fun `Android 기본 뒤로가기는 기존처럼 화면 슬라이드 없이 완료된다`() {
        val fixture = Fixture(AppRoute.AddAlarm, usePlatformDefaults = true)
        launch(fixture).use {
            compose.onNodeWithTag("draft-AddAlarm").assertIsDisplayed()
            withManualClock {
                compose.onNodeWithTag("back-AddAlarm").performClick()
                advanceTransition(64)
                compose.runOnIdle {
                    assertFalse(supportsPredictiveBackTransitions)
                    assertEquals(listOf(AppRoute.Home), fixture.navigation.backStack)
                    assertEquals(1, fixture.disposalCount(AppRoute.AddAlarm))
                    assertTrue(fixture.positionHistory.getValue(AppRoute.AddAlarm).all { abs(it) < 1f })
                }
            }
            compose.onNodeWithTag("draft-Home").assertIsDisplayed()
        }
    }

    private fun launch(fixture: Fixture): FontScaleTestContent = launchWithFontScale(1f) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides fixture) {
            DisposableEffect(fixture) {
                fixture.navigationEventDispatcher.addInput(fixture.input)
                onDispose { fixture.navigationEventDispatcher.dispose() }
            }
            RingoutNavHost(
                navigationState = fixture.navigation,
                displayedRoute = fixture.navigation.requestedRoute,
                viewModelStoreProvider = rememberViewModelStoreProvider(),
                isBackBlocked = fixture.isBackBlocked,
                onBack = { route ->
                    fixture.navigationBackCalls++
                    fixture.navigation.popBackStack(route)
                },
                backTransitions = if (fixture.usePlatformDefaults) platformNavigationBackTransitions() else fixture.transitions,
                graph = {
                    entry(AppRoute.Home) { TestEntry(AppRoute.Home, fixture) }
                    entry(AppRoute.Social) { TestEntry(AppRoute.Social, fixture) }
                    entry(AppRoute.AddAlarm) { TestEntry(AppRoute.AddAlarm, fixture) }
                    entry<AppRoute.Destination> { TestEntry(it, fixture) }
                    entry(AppRoute.AlarmSound) { TestEntry(AppRoute.AlarmSound, fixture) }
                    entry(AppRoute.MyPage) { TestEntry(AppRoute.MyPage, fixture) }
                },
            )
        }
    }

    @Composable
    private fun TestEntry(route: AppRoute, fixture: Fixture) {
        var draft by rememberSaveable { mutableStateOf("초기 설정") }
        if (fixture.useEntryBackAction) {
            val isEntrySaving = fixture.isEntrySaving
            val onBack = {
                if (!isEntrySaving) {
                    fixture.entryBackCalls++
                    fixture.navigation.popBackStack(route)
                }
            }
            NavigationBackAction(onBack = onBack)
            NavigationBackHandler(
                state = rememberNavigationEventState(NavigationEventInfo.None),
                isBackEnabled = isEntrySaving,
                onBackCompleted = onBack,
            )
        }
        DisposableEffect(route) {
            onDispose { fixture.disposals[route] = fixture.disposalCount(route) + 1 }
        }
        Column(
            Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                val x = coordinates.positionInRoot().x
                fixture.positions[route] = x
                fixture.widths[route] = coordinates.size.width
                fixture.positionHistory.getOrPut(route) { mutableListOf() }.add(x)
            },
        ) {
            BasicTextField(draft, { draft = it }, Modifier.testTag("draft-${route.tag()}"))
            Button(
                onClick = { if (!fixture.isBackBlocked) fixture.navigation.popBackStack(route) },
                modifier = Modifier.testTag("back-${route.tag()}"),
            ) { Text("뒤로가기") }
        }
    }

    private fun withManualClock(block: () -> Unit) {
        compose.mainClock.autoAdvance = false
        try {
            block()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun advanceTransition(milliseconds: Long) {
        // Navigation events and seekable transitions resume on the UI dispatcher between frames.
        // Drain each frame so a batched clock jump cannot leave completion/cancellation queued.
        repeat((milliseconds / 16).toInt() + 1) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private class Fixture(initialRoute: AppRoute, val usePlatformDefaults: Boolean = false) : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
        val input = GestureInput()
        val navigation = AppNavigationState().apply {
            if (initialRoute is AppRoute.Destination || initialRoute == AppRoute.AlarmSound) navigate(AppRoute.AddAlarm)
            navigate(initialRoute)
        }
        var isBackBlocked by mutableStateOf(false)
        var isEntrySaving by mutableStateOf(false)
        var useEntryBackAction = false
        var entryBackCalls = 0
        var navigationBackCalls = 0
        var popTransitions = 0
        var predictiveTransitions = 0
        val positions = mutableMapOf<AppRoute, Float>()
        val widths = mutableMapOf<AppRoute, Int>()
        val positionHistory = mutableMapOf<AppRoute, MutableList<Float>>()
        val disposals = mutableMapOf<AppRoute, Int>()
        val transitions = NavigationBackTransitions(
            enabled = true,
            pop = { popTransitions++; slideBack() },
            predictivePop = { predictiveTransitions++; slideBack() },
        )

        fun disposalCount(route: AppRoute) = disposals[route] ?: 0

        fun assertPartiallyMoved(route: AppRoute) {
            val x = positions.getValue(route)
            assertTrue(x > 0f && x < widths.getValue(route), "화면이 이동 중이어야 한다: x=$x")
        }
    }

    private class GestureInput : NavigationEventInput() {
        fun start() = dispatchOnBackStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
        fun progress(progress: Float) = dispatchOnBackProgressed(
            NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = progress),
        )
        fun complete() = dispatchOnBackCompleted()
        fun cancel() = dispatchOnBackCancelled()
    }

    private companion object {
        fun AppRoute.tag() = when (this) {
            is AppRoute.Destination -> "Destination"
            else -> toString()
        }

        fun slideBack() = slideInHorizontally(tween(600, easing = LinearEasing)) { -it / 3 } togetherWith
            slideOutHorizontally(tween(600, easing = LinearEasing)) { it }
    }
}
