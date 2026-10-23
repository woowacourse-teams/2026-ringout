package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
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
import com.joon.ringout.analytics.AnalyticsOnboardingStep
import com.joon.ringout.analytics.NoOpOnboardingAnalyticsRecorder
import com.joon.ringout.analytics.OnboardingAnalyticsRecorder
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

            compose.onNodeWithText("다음으로").performClick()
            assertPage(OnboardingStep.LocationPermission)
            compose.onNodeWithText("다음으로").performClick()
            assertPage(OnboardingStep.NotificationPermission)
            compose.onNodeWithText("다음으로").performClick()
            compose.runOnIdle { assertEquals(1, fixture.completions) }
        }
    }

    @Test
    fun `뒤로 가기로 외부 단계가 바뀌면 페이지와 버튼도 이전 단계로 돌아온다`() {
        val fixture = Fixture(OnboardingStep.Departure)
        launch(fixture).use {
            assertPage(OnboardingStep.Departure)
            compose.onNodeWithText("다음으로").assertIsDisplayed()

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
    fun `소개 전환 중 연속 다음 입력은 권한 안내까지만 이동하고 권한 요청은 도착 뒤에 처리한다`() {
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
                    OnboardingStep.LocationPermission,
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

                // 권한 페이지를 향해 이동 중인 추가 탭은 다음 권한으로 넘어가지 않는다.
                repeat(2) {
                    compose.onNodeWithText("다음으로").performClick()
                    compose.mainClock.advanceTimeByFrame()
                }
                compose.runOnIdle {
                    assertEquals(OnboardingStep.LocationPermission, fixture.state.step)
                    assertEquals(0, fixture.completions)
                }
                compose.onNodeWithText("시작하기").assertDoesNotExist()

                compose.mainClock.advanceTimeBy(2_000)
            } finally {
                compose.mainClock.autoAdvance = true
            }

            assertPage(OnboardingStep.LocationPermission)
            compose.onNodeWithText("다음으로").assertIsEnabled().performClick()
            assertPage(OnboardingStep.NotificationPermission)
            compose.runOnIdle { assertEquals(0, fixture.completions) }
            compose.onNodeWithText("다음으로").assertIsEnabled().performClick()
            compose.runOnIdle { assertEquals(1, fixture.completions) }
        }
    }

    @Test
    fun `다시 그리기 전 연속 다음 클릭은 위치 권한까지만 진행하고 온보딩을 완료하지 않는다`() {
        val completedStepCounts = mutableListOf<Int>()
        val analytics = RecordingOnboardingAnalytics()
        launchWithFontScale(1f) {
            RingoutTheme(themeMode = ThemeMode.Dark) {
                OnboardingRoute(
                    analytics = analytics,
                    onComplete = { completedStepCounts += it },
                    permissionController = FakePermissionController(),
                )
            }
        }.use {
            assertPage(OnboardingStep.Introduction)
            compose.mainClock.autoAdvance = false
            try {
                // 같은 콜백에 도착한 연속 입력도 최신 ViewModel 상태를 기준으로 처리한다.
                compose.onNodeWithText("다음으로")
                    .performSemanticsAction(SemanticsActions.OnClick) { click ->
                        repeat(8) { assertTrue(click()) }
                    }
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(96)

                compose.onNodeWithText("다음으로").assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithText("시작하기").assertDoesNotExist()
                compose.runOnIdle {
                    assertTrue(completedStepCounts.isEmpty())
                    assertEquals(listOf(AnalyticsOnboardingStep.Introduction), analytics.viewedSteps)
                }

                compose.mainClock.advanceTimeBy(2_000)
            } finally {
                compose.mainClock.autoAdvance = true
            }

            assertPage(OnboardingStep.LocationPermission)
            compose.runOnIdle {
                assertEquals(
                    listOf(AnalyticsOnboardingStep.Introduction, AnalyticsOnboardingStep.LocationPermission),
                    analytics.viewedSteps,
                )
            }
            compose.onNodeWithText("다음으로").assertIsEnabled().performClick()
            assertPage(OnboardingStep.NotificationPermission)
            compose.onNodeWithText("다음으로").assertIsEnabled().performClick()
            compose.runOnIdle { assertEquals(listOf(6), completedStepCounts) }
        }
    }

    @Test
    fun `전환 도중 다음을 연속으로 누르면 지나친 페이지 대신 마지막 도착 페이지만 조회로 기록한다`() {
        val analytics = RecordingOnboardingAnalytics()
        launchWithFontScale(1f) {
            RingoutTheme(themeMode = ThemeMode.Dark) {
                OnboardingRoute(analytics = analytics, onComplete = {}, permissionController = FakePermissionController())
            }
        }.use {
            assertPage(OnboardingStep.Introduction)
            compose.mainClock.autoAdvance = false
            try {
                repeat(3) {
                    compose.onNodeWithText("다음으로").performClick()
                    compose.mainClock.advanceTimeByFrame()
                    compose.mainClock.advanceTimeBy(96)
                    compose.runOnIdle {
                        assertEquals(listOf(AnalyticsOnboardingStep.Introduction), analytics.viewedSteps)
                    }
                }
                compose.mainClock.advanceTimeBy(2_000)
            } finally {
                compose.mainClock.autoAdvance = true
            }

            assertPage(OnboardingStep.Departure)
            compose.runOnIdle {
                assertEquals(
                    listOf(AnalyticsOnboardingStep.Introduction, AnalyticsOnboardingStep.Departure),
                    analytics.viewedSteps,
                )
            }
        }
    }

    @Test
    fun `스와이프 도중에는 조회를 기록하지 않고 도착과 이전 페이지 재방문만 기록한다`() {
        val analytics = RecordingOnboardingAnalytics()
        var theme by mutableStateOf(ThemeMode.Dark)
        launchWithFontScale(1f) {
            RingoutTheme(themeMode = theme) {
                OnboardingRoute(analytics = analytics, onComplete = {}, permissionController = FakePermissionController())
            }
        }.use {
            assertPage(OnboardingStep.Introduction)
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithTag("onboarding-pager").performTouchInput {
                    down(Offset(width * 0.9f, centerY))
                    moveTo(Offset(width * 0.6f, centerY))
                    moveTo(Offset(width * 0.2f, centerY))
                }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    assertEquals(listOf(AnalyticsOnboardingStep.Introduction), analytics.viewedSteps)
                }
                compose.onNodeWithTag("onboarding-pager").performTouchInput { up() }
                compose.mainClock.advanceTimeBy(2_000)
            } finally {
                compose.mainClock.autoAdvance = true
            }

            assertPage(OnboardingStep.Destination)
            compose.runOnIdle {
                assertEquals(
                    listOf(AnalyticsOnboardingStep.Introduction, AnalyticsOnboardingStep.Destination),
                    analytics.viewedSteps,
                )
                theme = ThemeMode.Light
            }
            compose.onNodeWithTag("onboarding-pager").performTouchInput { swipeRight() }
            assertPage(OnboardingStep.Introduction)
            compose.runOnIdle {
                assertEquals(
                    listOf(
                        AnalyticsOnboardingStep.Introduction,
                        AnalyticsOnboardingStep.Destination,
                        AnalyticsOnboardingStep.Introduction,
                    ),
                    analytics.viewedSteps,
                )
            }
        }
    }

    @Test
    fun `완료 저장 중에는 시작 버튼과 페이지 이동을 막아 중복 완료를 방지한다`() {
        val fixture = Fixture(OnboardingStep.NotificationPermission).apply { completionEnabled = false }
        launch(fixture).use {
            compose.onNodeWithText("시작하는 중…").assertIsDisplayed().assertIsNotEnabled()
                .performTouchInput { click() }
            compose.onNodeWithTag("onboarding-pager").performTouchInput { swipeRight() }

            assertPage(OnboardingStep.NotificationPermission)
            compose.runOnIdle { assertEquals(0, fixture.completions) }
        }
    }

    @Test
    fun `완료 저장에 실패하면 오류 안내와 재시도 버튼으로 다시 완료할 수 있다`() {
        val fixture = Fixture(OnboardingStep.NotificationPermission).apply { completionFailed = true }
        launch(fixture).use {
            compose.onNodeWithText("시작하지 못했어요. 다시 시도해주세요.").assertIsDisplayed()
            compose.onNodeWithText("다시 시도").assertIsDisplayed().performClick()

            compose.runOnIdle { assertEquals(1, fixture.completions) }
        }
    }

    @Test
    fun `소개 마지막에서 연속으로 스와이프해도 위치 권한 안내를 건너뛰지 않는다`() {
        val fixture = Fixture(OnboardingStep.Departure)
        launch(fixture).use {
            compose.onNodeWithTag("onboarding-pager").performTouchInput {
                repeat(3) { swipeLeft() }
            }
            assertPage(OnboardingStep.LocationPermission)
            compose.runOnIdle { assertEquals(OnboardingStep.LocationPermission, fixture.state.step) }
        }
    }

    @Test
    fun `권한 안내에서는 좌우 스와이프로 권한을 건너뛰거나 이전 단계로 이동할 수 없다`() {
        val fixture = Fixture(OnboardingStep.LocationPermission)
        launch(fixture).use {
            compose.onNodeWithTag("onboarding-pager").performTouchInput { swipeLeft() }
            assertPage(OnboardingStep.LocationPermission)
            compose.onNodeWithTag("onboarding-pager").performTouchInput { swipeRight() }
            assertPage(OnboardingStep.LocationPermission)
            compose.runOnIdle { assertEquals(OnboardingStep.LocationPermission, fixture.state.step) }
        }
    }

    @Test
    fun `권한 상태를 확인하기 전에는 다음 버튼을 비활성화한다`() {
        val fixture = Fixture(OnboardingStep.LocationPermission).apply {
            state = state.copy(permissions = OnboardingPermissionState())
        }
        launch(fixture).use {
            compose.onNodeWithText("권한 확인 중…").assertIsDisplayed().assertIsNotEnabled()
                .performTouchInput { click() }
            compose.runOnIdle { assertEquals(OnboardingStep.LocationPermission, fixture.state.step) }
        }
    }

    @Test
    fun `위치와 알림을 차례로 허용해야 온보딩을 완료한다`() {
        val controller = FakePermissionController(
            permissions = OnboardingPermissionState(
                location = OnboardingPermissionStatus.Requestable,
                notifications = OnboardingPermissionStatus.Requestable,
            ),
        )
        var completions = 0
        launchRoute(controller) { completions++ }.use {
            advanceToLocationPermission()
            compose.runOnIdle { assertTrue(controller.requests.isEmpty()) }

            compose.onNodeWithText("다음으로").performClick()
            assertPage(OnboardingStep.NotificationPermission)
            compose.runOnIdle {
                assertEquals(listOf(OnboardingPermission.Location), controller.requests)
                assertEquals(0, completions)
            }
            compose.onNodeWithText("다음으로").performClick()
            compose.runOnIdle {
                assertEquals(listOf(OnboardingPermission.Location, OnboardingPermission.Notifications), controller.requests)
                assertEquals(1, completions)
            }
        }
    }

    @Test
    fun `위치 권한을 거부하면 안내를 유지하고 설정에서 허용한 뒤 알림으로 진행한다`() {
        val controller = FakePermissionController(
            permissions = OnboardingPermissionState(
                location = OnboardingPermissionStatus.Requestable,
                notifications = OnboardingPermissionStatus.Requestable,
            ),
            requestResult = OnboardingPermissionStatus.SettingsRequired,
        )
        var completions = 0
        launchRoute(controller) { completions++ }.use {
            advanceToLocationPermission()
            compose.onNodeWithText("다음으로").performClick()

            assertPage(OnboardingStep.LocationPermission)
            compose.onNodeWithText("설정에서 위치 권한을 허용하고\n정확한 위치를 켜주세요.").assertIsDisplayed()
            compose.onNodeWithText("설정 열기").assertIsEnabled()
            compose.runOnIdle {
                assertEquals(0, completions)
                controller.requestResult = OnboardingPermissionStatus.Granted
            }
            compose.onNodeWithText("설정 열기").performClick()
            assertPage(OnboardingStep.NotificationPermission)
            compose.runOnIdle {
                assertEquals(listOf(OnboardingPermission.Location, OnboardingPermission.Location), controller.requests)
                assertEquals(0, completions)
            }
        }
    }

    @Test
    fun `이미 허용한 권한은 팝업 없이 확인하고 다음 안내로 진행한다`() {
        val controller = FakePermissionController()
        var completions = 0
        launchRoute(controller) { completions++ }.use {
            advanceToLocationPermission()
            compose.onNodeWithText("다음으로").performClick()
            assertPage(OnboardingStep.NotificationPermission)
            compose.onNodeWithText("다음으로").performClick()
            compose.runOnIdle {
                assertEquals(0, controller.systemPrompts)
                assertEquals(1, completions)
            }
        }
    }

    @Test
    fun `시스템 권한 응답을 기다리는 동안 중복 요청을 막고 허용 후에만 이동한다`() {
        val controller = FakePermissionController(
            permissions = OnboardingPermissionState(
                location = OnboardingPermissionStatus.Requestable,
                notifications = OnboardingPermissionStatus.Requestable,
            ),
            deferRequests = true,
        )
        launchRoute(controller).use {
            advanceToLocationPermission()
            compose.onNodeWithText("다음으로").performClick()
            compose.onNodeWithText("권한 확인 중…").assertIsNotEnabled().performTouchInput { click() }
            assertPage(OnboardingStep.LocationPermission)
            compose.runOnIdle {
                assertEquals(listOf(OnboardingPermission.Location), controller.requests)
                controller.finishRequest(OnboardingPermissionStatus.Granted)
            }
            assertPage(OnboardingStep.NotificationPermission)
        }
    }

    @Test
    fun `라이트와 다크 테마에서 소개와 두 권한 페이지의 안내와 하단 버튼을 표시한다`() {
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
                    compose.onNodeWithText("다음으로").assertIsDisplayed()
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
                    val button = compose.onNodeWithText("다음으로")
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
                    compose.onNodeWithText("다음으로", useUnmergedTree = true).assertTextFits()
                    compose.runOnIdle {
                        content.captureLayout("onboarding-compact-${theme.name.lowercase()}-${step.name.lowercase()}-description")
                    }
                }
                compose.onNodeWithText("다음으로").performClick()
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
                onPageSettled = {},
                onNext = fixture::onNext,
                completionEnabled = fixture.completionEnabled,
                completionFailed = fixture.completionFailed,
            )
        }
    }

    private fun launchRoute(
        controller: OnboardingPermissionController,
        onComplete: () -> Unit = {},
    ): FontScaleTestContent = launchWithFontScale(1f) {
        RingoutTheme(themeMode = ThemeMode.Dark) {
            OnboardingRoute(
                analytics = NoOpOnboardingAnalyticsRecorder,
                onComplete = { onComplete() },
                permissionController = controller,
            )
        }
    }

    private fun advanceToLocationPermission() {
        repeat(4) { compose.onNodeWithText("다음으로").performClick() }
        assertPage(OnboardingStep.LocationPermission)
    }

    private fun assertPage(step: OnboardingStep) {
        if (step.permission == null) {
            compose.onNodeWithContentDescription("현재 페이지 ${step.ordinal + 1}, 전체 4").assertIsDisplayed()
        } else {
            compose.onNodeWithContentDescription("현재 페이지 4, 전체 4").assertDoesNotExist()
        }
        compose.onNodeWithTag("onboarding-page-${step.name}").assertIsDisplayed()
    }

    private class Fixture(step: OnboardingStep = OnboardingStep.Introduction) {
        var state by mutableStateOf(
            OnboardingUiState(
                step = step,
                permissions = OnboardingPermissionState(
                    location = OnboardingPermissionStatus.Granted,
                    notifications = OnboardingPermissionStatus.Granted,
                ),
            ),
        )
        var completionEnabled by mutableStateOf(true)
        var completionFailed by mutableStateOf(false)
        var completions = 0

        fun onNext() {
            if (state.isLastStep) {
                completions++
            } else {
                state = state.copy(step = OnboardingStep.entries[state.step.ordinal + 1])
            }
        }
    }

    private class RecordingOnboardingAnalytics : OnboardingAnalyticsRecorder by NoOpOnboardingAnalyticsRecorder {
        val viewedSteps = mutableListOf<AnalyticsOnboardingStep>()

        override fun recordOnboardingStepViewed(step: AnalyticsOnboardingStep, stepCount: Int) {
            assertEquals(6, stepCount)
            viewedSteps += step
        }
    }

    private class FakePermissionController(
        var permissions: OnboardingPermissionState = OnboardingPermissionState(
            location = OnboardingPermissionStatus.Granted,
            notifications = OnboardingPermissionStatus.Granted,
        ),
        var requestResult: OnboardingPermissionStatus = OnboardingPermissionStatus.Granted,
        val deferRequests: Boolean = false,
    ) : OnboardingPermissionController {
        val requests = mutableListOf<OnboardingPermission>()
        var systemPrompts = 0
        private var pendingRequest: ((OnboardingPermissionStatus) -> Unit)? = null

        override fun refresh(onResult: (OnboardingPermissionState) -> Unit) = onResult(permissions)

        override fun request(permission: OnboardingPermission, onResult: (OnboardingPermissionStatus) -> Unit) {
            requests += permission
            if (permissions[permission] != OnboardingPermissionStatus.Granted) systemPrompts++
            val completion: (OnboardingPermissionStatus) -> Unit = { status ->
                permissions = permissions.withStatus(permission, status)
                onResult(status)
            }
            if (deferRequests) {
                pendingRequest = completion
            } else {
                completion(requestResult)
            }
        }

        fun finishRequest(status: OnboardingPermissionStatus) {
            val completion = requireNotNull(pendingRequest)
            pendingRequest = null
            completion(status)
        }
    }
}
