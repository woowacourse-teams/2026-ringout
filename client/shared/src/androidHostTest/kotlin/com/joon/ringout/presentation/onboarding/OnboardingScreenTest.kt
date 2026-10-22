package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.analytics.NoOpOnboardingAnalyticsRecorder
import com.joon.ringout.presentation.FontScaleTestContent
import com.joon.ringout.presentation.assertTextFits
import com.joon.ringout.presentation.launchWithFontScale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `스와이프와 다음 버튼을 번갈아 사용해도 현재 페이지와 완료 동작이 일치한다`() {
        val fixture = Fixture()
        launch(fixture).use {
            assertPage(OnboardingStep.Introduction)

            compose.onNodeWithTag("onboarding-pager").performTouchInput { swipeLeft() }
            assertPage(OnboardingStep.Destination)

            compose.onNodeWithText("다음으로").performClick()
            assertPage(OnboardingStep.Interval)

            compose.onNodeWithTag("onboarding-pager").performTouchInput { swipeRight() }
            assertPage(OnboardingStep.Destination)

            compose.onNodeWithText("다음으로").performClick()
            assertPage(OnboardingStep.Interval)
            compose.onNodeWithText("다음으로").performClick()
            assertPage(OnboardingStep.Departure)
            compose.runOnIdle { assertEquals(0, fixture.completions) }

            compose.onNodeWithText("시작하기").performClick()
            compose.runOnIdle { assertEquals(1, fixture.completions) }
        }
    }

    @Test
    fun `뒤로 가기로 외부 단계가 바뀌면 페이지와 버튼도 이전 단계로 돌아온다`() {
        val fixture = Fixture(OnboardingStep.Departure)
        launch(fixture).use {
            assertPage(OnboardingStep.Departure)
            compose.onNodeWithText("시작하기").assertIsDisplayed()

            compose.runOnIdle { fixture.state = fixture.state.copy(step = OnboardingStep.Interval) }
            assertPage(OnboardingStep.Interval)
            compose.onNodeWithText("다음으로").assertIsDisplayed()

            compose.onNodeWithTag("onboarding-pager").performTouchInput { swipeRight() }
            assertPage(OnboardingStep.Destination)
            compose.runOnIdle { assertEquals(OnboardingStep.Destination, fixture.state.step) }
        }
    }

    @Test
    fun `페이지 이동 애니메이션 중 연속으로 뒤로 가도 마지막 요청한 페이지를 유지한다`() {
        val fixture = Fixture(OnboardingStep.Departure)
        launch(fixture).use {
            assertPage(OnboardingStep.Departure)
            compose.mainClock.autoAdvance = false
            try {
                // 각 이동을 끝내기 전에 다음 뒤로 가기를 입력해 애니메이션을 취소한다.
                listOf(OnboardingStep.Interval, OnboardingStep.Destination).forEach { step ->
                    compose.runOnIdle { fixture.state = fixture.state.copy(step = step) }
                    compose.mainClock.advanceTimeByFrame()
                    compose.mainClock.advanceTimeBy(96)
                }
                compose.runOnIdle { fixture.state = fixture.state.copy(step = OnboardingStep.Introduction) }
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(2_000)
            } finally {
                compose.mainClock.autoAdvance = true
            }

            assertPage(OnboardingStep.Introduction)
            compose.runOnIdle { assertEquals(OnboardingStep.Introduction, fixture.state.step) }
            compose.onNodeWithText("다음으로").performClick()
            assertPage(OnboardingStep.Destination)
        }
    }

    @Test
    fun `페이지 전환 중 다음 버튼을 누를 때마다 목표 페이지를 앞당기고 마지막 도착 후에만 완료할 수 있다`() {
        val fixture = Fixture()
        launch(fixture).use {
            assertPage(OnboardingStep.Introduction)
            compose.mainClock.autoAdvance = false
            try {
                // 매번 애니메이션이 끝나기 전에 다시 눌러 새 목표로 이동시킨다.
                listOf(
                    OnboardingStep.Destination,
                    OnboardingStep.Interval,
                    OnboardingStep.Departure,
                ).forEach { expectedStep ->
                    compose.onNodeWithText("다음으로").assertIsEnabled().performClick()
                    compose.mainClock.advanceTimeByFrame()
                    compose.mainClock.advanceTimeBy(96)

                    compose.runOnIdle {
                        assertEquals(expectedStep, fixture.state.step)
                        assertEquals(0, fixture.completions)
                    }
                    compose.onNodeWithText("다음으로").assertIsDisplayed().assertIsEnabled()
                    compose.onNodeWithText("시작하기").assertDoesNotExist()
                }

                // 마지막 페이지를 향해 이동 중인 추가 탭은 완료 요청이 되지 않는다.
                repeat(2) {
                    compose.onNodeWithText("다음으로").performClick()
                    compose.mainClock.advanceTimeByFrame()
                }
                compose.runOnIdle {
                    assertEquals(OnboardingStep.Departure, fixture.state.step)
                    assertEquals(0, fixture.completions)
                }
                compose.onNodeWithText("시작하기").assertDoesNotExist()

                compose.mainClock.advanceTimeBy(2_000)
            } finally {
                compose.mainClock.autoAdvance = true
            }

            assertPage(OnboardingStep.Departure)
            compose.onNodeWithText("다음으로").assertDoesNotExist()
            compose.onNodeWithText("시작하기").assertIsEnabled().performClick()
            compose.runOnIdle { assertEquals(1, fixture.completions) }
        }
    }

    @Test
    fun `다시 그리기 전 연속 다음 클릭은 마지막 페이지까지 진행하되 온보딩을 완료하지 않는다`() {
        val completedStepCounts = mutableListOf<Int>()
        launchWithFontScale(1f) {
            RingoutTheme(themeMode = ThemeMode.Dark) {
                OnboardingRoute(
                    analytics = NoOpOnboardingAnalyticsRecorder,
                    onComplete = { completedStepCounts += it },
                )
            }
        }.use {
            assertPage(OnboardingStep.Introduction)
            compose.mainClock.autoAdvance = false
            try {
                // 같은 콜백에 도착한 연속 입력도 최신 ViewModel 상태를 기준으로 처리한다.
                compose.onNodeWithText("다음으로")
                    .performSemanticsAction(SemanticsActions.OnClick) { click ->
                        repeat(4) { assertTrue(click()) }
                    }
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(96)

                compose.onNodeWithContentDescription("현재 페이지 4, 전체 4").assertIsDisplayed()
                compose.onNodeWithText("다음으로").assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithText("시작하기").assertDoesNotExist()
                compose.runOnIdle { assertTrue(completedStepCounts.isEmpty()) }

                compose.mainClock.advanceTimeBy(2_000)
            } finally {
                compose.mainClock.autoAdvance = true
            }

            assertPage(OnboardingStep.Departure)
            compose.onNodeWithText("시작하기").assertIsEnabled().performClick()
            compose.runOnIdle { assertEquals(listOf(4), completedStepCounts) }
        }
    }

    @Test
    fun `완료 저장 중에는 시작 버튼과 페이지 이동을 막아 중복 완료를 방지한다`() {
        val fixture = Fixture(OnboardingStep.Departure).apply { completionEnabled = false }
        launch(fixture).use {
            compose.onNodeWithText("시작하는 중…").assertIsDisplayed().assertIsNotEnabled()
                .performTouchInput { click() }
            compose.onNodeWithTag("onboarding-pager").performTouchInput { swipeRight() }

            assertPage(OnboardingStep.Departure)
            compose.runOnIdle { assertEquals(0, fixture.completions) }
        }
    }

    @Test
    fun `완료 저장에 실패하면 오류 안내와 재시도 버튼으로 다시 완료할 수 있다`() {
        val fixture = Fixture(OnboardingStep.Departure).apply { completionFailed = true }
        launch(fixture).use {
            compose.onNodeWithText("시작하지 못했어요. 다시 시도해주세요.").assertIsDisplayed()
            compose.onNodeWithText("다시 시도").assertIsDisplayed().performClick()

            compose.runOnIdle { assertEquals(1, fixture.completions) }
        }
    }

    @Test
    fun `라이트와 다크 테마에서 네 페이지의 안내와 하단 버튼을 표시한다`() {
        ThemeMode.entries.forEach { theme ->
            val fixture = Fixture()
            launch(fixture, theme = theme).use { content ->
                OnboardingStep.entries.forEach { step ->
                    compose.runOnIdle { fixture.state = fixture.state.copy(step = step) }
                    assertPage(step)
                    compose.onNodeWithText(step.pageContent().title, useUnmergedTree = true)
                        .assertIsDisplayed().assertTextFits()
                    compose.onNodeWithText(step.pageContent().description, useUnmergedTree = true)
                        .assertIsDisplayed().assertTextFits()
                    compose.onNodeWithText(step.buttonLabel()).assertIsDisplayed()
                    compose.runOnIdle {
                        content.captureLayout("onboarding-${theme.name.lowercase()}-${step.name.lowercase()}")
                    }
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `작은 화면과 두 배 글자 크기에서 설명을 끝까지 읽어도 하단 버튼을 사용할 수 있다`() {
        ThemeMode.entries.forEach { theme ->
            val fixture = Fixture()
            launch(fixture, theme = theme, fontScale = 2f).use { content ->
                OnboardingStep.entries.forEach { step ->
                    compose.runOnIdle { fixture.state = fixture.state.copy(step = step) }
                    assertPage(step)
                    val button = compose.onNodeWithText(step.buttonLabel())
                    button.assertIsDisplayed()
                    val buttonTop = button.fetchSemanticsNode().boundsInRoot.top

                    compose.onNodeWithText(step.pageContent().title, useUnmergedTree = true)
                        .performScrollTo().assertIsDisplayed().assertTextFits()
                    compose.runOnIdle {
                        content.captureLayout("onboarding-compact-${theme.name.lowercase()}-${step.name.lowercase()}-title")
                    }
                    val description = compose.onNodeWithText(step.pageContent().description, useUnmergedTree = true)
                        .performScrollTo().assertIsDisplayed().assertTextFits()

                    button.assertIsDisplayed()
                    assertEquals(buttonTop, button.fetchSemanticsNode().boundsInRoot.top, 1f)
                    assertTrue(description.fetchSemanticsNode().boundsInRoot.bottom <= buttonTop)
                    compose.onNodeWithText(step.buttonLabel(), useUnmergedTree = true).assertTextFits()
                    compose.runOnIdle {
                        content.captureLayout("onboarding-compact-${theme.name.lowercase()}-${step.name.lowercase()}-description")
                    }
                }
                compose.onNodeWithText("시작하기").performClick()
                compose.runOnIdle { assertEquals(1, fixture.completions) }
            }
        }
    }

    private fun launch(
        fixture: Fixture,
        theme: ThemeMode = ThemeMode.Dark,
        fontScale: Float = 1f,
    ): FontScaleTestContent = launchWithFontScale(fontScale) {
        RingoutTheme(themeMode = theme) {
            OnboardingScreen(
                uiState = fixture.state,
                onPageChanged = { fixture.state = fixture.state.copy(step = OnboardingStep.entries[it]) },
                onNext = fixture::onNext,
                completionEnabled = fixture.completionEnabled,
                completionFailed = fixture.completionFailed,
            )
        }
    }

    private fun assertPage(step: OnboardingStep) {
        compose.onNodeWithContentDescription("현재 페이지 ${step.ordinal + 1}, 전체 4").assertIsDisplayed()
        compose.onNodeWithTag("onboarding-page-${step.name}").assertIsDisplayed()
    }

    private class Fixture(step: OnboardingStep = OnboardingStep.Introduction) {
        var state by mutableStateOf(OnboardingUiState(step = step))
        var completionEnabled by mutableStateOf(true)
        var completionFailed by mutableStateOf(false)
        var completions = 0

        fun onNext() {
            if (state.step == OnboardingStep.Departure) {
                completions++
            } else {
                state = state.copy(step = OnboardingStep.entries[state.step.ordinal + 1])
            }
        }
    }
}

private fun OnboardingStep.buttonLabel() = if (this == OnboardingStep.Departure) "시작하기" else "다음으로"
