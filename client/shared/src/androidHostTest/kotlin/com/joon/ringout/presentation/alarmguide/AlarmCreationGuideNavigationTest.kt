package com.joon.ringout.presentation.alarmguide

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.joon.ringout.presentation.FontScaleTestContent
import com.joon.ringout.presentation.launchWithFontScale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
class AlarmCreationGuideNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `공통 뒤로가기 제스처는 작성 중인 알람을 닫지 않고 가이드만 종료한다`() {
        val fixture = Fixture()
        launch(fixture).use {
            compose.onNodeWithTag("guide-overlay").assertIsDisplayed()

            compose.runOnIdle { fixture.input.start() }
            compose.runOnIdle {
                fixture.input.progress()
                assertEquals(0, fixture.finishCalls)
                assertEquals(0, fixture.editorDisposals)
            }
            compose.runOnIdle { fixture.input.complete() }

            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            compose.onNodeWithText("작성 중인 알람 07:20").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf(Route.Home, Route.Editor), fixture.backStack.toList())
                assertEquals(1, fixture.finishCalls)
                assertEquals(0, fixture.editorDisposals)
                assertEquals(0, fixture.navigationBackCalls)
            }
        }
    }

    @Test
    fun `공통 뒤로가기 제스처를 취소하면 가이드와 작성 화면을 유지한다`() {
        val fixture = Fixture()
        launch(fixture).use {
            compose.onNodeWithTag("guide-overlay").assertIsDisplayed()
            compose.runOnIdle {
                fixture.input.start()
                fixture.input.progress()
                fixture.input.cancel()
            }

            compose.onNodeWithTag("guide-overlay").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf(Route.Home, Route.Editor), fixture.backStack.toList())
                assertEquals(0, fixture.finishCalls)
                assertEquals(0, fixture.editorDisposals)
                assertFalse(fixture.viewModel.uiState.value.isFinishing)
            }
        }
    }

    @Test
    fun `알람 저장 중 뒤로가기 제스처는 가이드를 종료하거나 작성 화면을 제거하지 않는다`() {
        val fixture = Fixture().apply { isAlarmSaving = true }
        launch(fixture).use {
            compose.onNodeWithTag("guide-overlay").assertIsDisplayed()
            compose.runOnIdle { fixture.input.gesture() }
            compose.runOnIdle {
                assertEquals(0, fixture.finishCalls)
                assertEquals(0, fixture.navigationBackCalls)
                assertEquals(listOf(Route.Home, Route.Editor), fixture.backStack.toList())
                fixture.isAlarmSaving = false
            }

            compose.runOnIdle { fixture.input.gesture() }
            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, fixture.finishCalls) }
        }
    }

    @Test
    fun `가이드 종료 저장 중 반복한 뒤로가기 제스처는 중복 종료나 화면 이동을 만들지 않는다`() {
        val fixture = Fixture().apply { holdFinish = true }
        launch(fixture).use {
            compose.onNodeWithTag("guide-overlay").assertIsDisplayed()
            compose.runOnIdle { fixture.input.gesture() }
            compose.runOnIdle { fixture.input.gesture() }

            compose.onNodeWithTag("guide-overlay").assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(fixture.viewModel.uiState.value.isFinishing)
                assertEquals(1, fixture.finishCalls)
                assertEquals(0, fixture.navigationBackCalls)
                assertEquals(0, fixture.editorDisposals)
                assertEquals(listOf(Route.Home, Route.Editor), fixture.backStack.toList())
            }
        }
    }

    @Test
    fun `목적지 안내 중 뒤로가기 제스처는 작성 화면을 유지하고 가이드만 종료한다`() =
        assertBackEndsGuideWithoutNavigation(AlarmCreationGuideStep.Destination)

    @Test
    fun `알람음 안내 중 뒤로가기 제스처는 작성 화면을 유지하고 가이드만 종료한다`() =
        assertBackEndsGuideWithoutNavigation(AlarmCreationGuideStep.Sound)

    private fun assertBackEndsGuideWithoutNavigation(step: AlarmCreationGuideStep) {
        val fixture = Fixture(step)
        launch(fixture).use {
            compose.onNodeWithTag("guide-overlay").assertIsDisplayed()
            compose.runOnIdle { fixture.input.gesture() }

            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            compose.onNodeWithText("작성 중인 알람 07:20").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(listOf(Route.Home, Route.Editor), fixture.backStack.toList())
                assertEquals(1, fixture.finishCalls)
                assertEquals(0, fixture.navigationBackCalls)
                assertEquals(0, fixture.editorDisposals)
            }
        }
    }

    private fun launch(fixture: Fixture): FontScaleTestContent = launchWithFontScale(1f) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides fixture) {
            DisposableEffect(fixture) {
                fixture.navigationEventDispatcher.addInput(fixture.input)
                onDispose { fixture.navigationEventDispatcher.dispose() }
            }
            val surface = when (fixture.backStack.last()) {
                Route.Home -> AlarmCreationGuideSurface.Home
                Route.Editor -> AlarmCreationGuideSurface.Editor
            }
            val guideState by fixture.viewModel.uiState.collectAsState()
            AlarmCreationGuideRoute(
                viewModel = fixture.viewModel,
                isPending = fixture.isPending,
                surface = surface,
                canPresent = true,
                isAlarmSaving = fixture.isAlarmSaving,
                isFinishing = guideState.isFinishing,
                finishFailed = false,
                onOpenEditor = { fixture.backStack.add(Route.Editor) },
                onFinish = fixture::finish,
            ) {
                // NavDisplay registers the same common back handler used by the production host.
                NavDisplay(
                    backStack = fixture.backStack,
                    onBack = {
                        fixture.navigationBackCalls++
                        fixture.backStack.removeLastOrNull()
                    },
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    entryProvider = entryProvider {
                        entry(Route.Home) { Text("홈") }
                        entry(Route.Editor) {
                            val draft = remember { "작성 중인 알람 07:20" }
                            DisposableEffect(Unit) {
                                onDispose { fixture.editorDisposals++ }
                            }
                            Text(draft)
                        }
                    },
                )
            }
        }
    }

    private enum class Route : NavKey { Home, Editor }

    private class Fixture(initialStep: AlarmCreationGuideStep = AlarmCreationGuideStep.Time) :
        NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
        val input = GestureInput()
        val backStack = NavBackStack<Route>(Route.Home, Route.Editor)
        val viewModel = AlarmCreationGuideViewModel()
        var isPending by mutableStateOf(true)
        var isAlarmSaving by mutableStateOf(false)
        var holdFinish = false
        var finishCalls = 0
        var editorDisposals = 0
        var navigationBackCalls = 0

        init {
            viewModel.onContextChanged(true, AlarmCreationGuideSurface.Home, true)
            while (viewModel.uiState.value.step != initialStep) {
                val step = requireNotNull(viewModel.uiState.value.step)
                check(viewModel.onNext(step))
                if (step == AlarmCreationGuideStep.AddAlarm) {
                    viewModel.onContextChanged(true, AlarmCreationGuideSurface.Editor, true)
                }
            }
        }

        fun finish() {
            finishCalls++
            if (!holdFinish) isPending = false
        }
    }

    /** Exercises common navigation events alongside the real NavDisplay back handler. */
    private class GestureInput : NavigationEventInput() {
        fun start() = dispatchOnBackStarted(NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT))
        fun progress() = dispatchOnBackProgressed(
            NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.5f),
        )
        fun complete() = dispatchOnBackCompleted()
        fun cancel() = dispatchOnBackCancelled()
        fun gesture() {
            start()
            progress()
            complete()
        }
    }
}
