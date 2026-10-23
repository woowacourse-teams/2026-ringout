package com.joon.ringout.presentation.alarmguide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.FontScaleTestContent
import com.joon.ringout.presentation.alarmguide.component.guideDescription
import com.joon.ringout.presentation.alarmsetup.AlarmSetupScreen
import com.joon.ringout.presentation.alarmsetup.AlarmSetupUiState
import com.joon.ringout.presentation.alarmsetup.to24HourString
import com.joon.ringout.presentation.alarmsetup.toAlarmTimePickerValue
import com.joon.ringout.presentation.assertTextFits
import com.joon.ringout.presentation.destination.DestinationSelection
import com.joon.ringout.presentation.home.HomeScreen
import com.joon.ringout.presentation.home.model.HomeUiState
import com.joon.ringout.presentation.launchWithFontScale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlarmCreationGuideTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `홈 안내는 딤드와 설명을 눌러도 유지되고 강조된 알람 추가 버튼을 눌러야 넘어간다`() {
        val fixture = Fixture()
        launch(fixture).use {
            compose.onNodeWithTag("guide-overlay").assertHasNoClickAction()
            compose.onNodeWithTag("guide-target").assertHasClickAction()
            pressDimmedArea()
            assertStep(AlarmCreationGuideStep.AddAlarm)
            compose.onNodeWithTag("guide-description").performTouchInput { click() }
            assertStep(AlarmCreationGuideStep.AddAlarm)
            compose.runOnIdle {
                assertEquals(AlarmCreationGuideSurface.Home, fixture.surface)
                assertEquals(0, fixture.addClicks)
            }
            pressTarget()
            assertStep(AlarmCreationGuideStep.Time)
            compose.runOnIdle {
                assertEquals(AlarmCreationGuideSurface.Editor, fixture.surface)
                assertEquals(1, fixture.addClicks)
            }
        }
    }

    @Test
    fun `설정 안내는 어디를 눌러도 넘어가며 마지막 시작하기 버튼으로 설정 변경 없이 종료한다`() {
        val fixture = Fixture()
        launch(fixture).use {
            val original = fixture.setup
            pressTarget()
            AlarmCreationGuideStep.entries.drop(1).dropLast(1).forEachIndexed { index, step ->
                assertStep(step)
                // Real controls are hidden from accessibility while the guide owns input.
                compose.onNodeWithText("월").assertDoesNotExist()
                when (index % 3) {
                    0 -> pressDimmedArea()
                    1 -> compose.onNodeWithTag("guide-description").performTouchInput { click() }
                    else -> pressTarget()
                }
            }
            assertStep(AlarmCreationGuideStep.CreateAlarm)
            compose.onNodeWithText("시작하기").performTouchInput { click() }
            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(1, fixture.addClicks)
                assertEquals(1, fixture.finishCalls)
                assertEquals(original, fixture.setup)
                assertEquals(0, fixture.inputChanges)
                assertEquals(0, fixture.destinationClicks)
                assertEquals(0, fixture.soundClicks)
                assertEquals(0, fixture.saveClicks)
                assertEquals(AlarmCreationGuideSurface.Editor, fixture.surface)
                assertFalse(fixture.isPending)
                assertFalse(fixture.setup.canSave)
            }
            compose.onNodeWithText("목적지를 설정해주세요").assertIsNotEnabled()
        }
    }

    @Test
    fun `시간과 반복 간격을 드래그해도 가이드 아래의 설정값이 바뀌지 않는다`() {
        val fixture = Fixture(AlarmCreationGuideStep.Time)
        launch(fixture).use {
            val original = fixture.setup
            compose.onNodeWithTag("guide-target").performTouchInput { swipeUp() }
            assertStep(AlarmCreationGuideStep.Time)
            repeat(3) { pressDimmedArea() }
            assertStep(AlarmCreationGuideStep.Interval)
            compose.onNodeWithTag("guide-target").performTouchInput { swipeLeft() }
            assertStep(AlarmCreationGuideStep.Interval)
            compose.runOnIdle {
                assertEquals(original, fixture.setup)
                assertEquals(0, fixture.inputChanges)
            }
        }
    }

    @Test
    fun `목적지와 알람음을 강조한 영역을 눌러도 선택 화면은 열리지 않는다`() {
        val fixture = Fixture(AlarmCreationGuideStep.Destination)
        launch(fixture).use {
            pressTarget()
            assertStep(AlarmCreationGuideStep.Interval)
            pressTarget()
            assertStep(AlarmCreationGuideStep.Sound)
            pressTarget()
            assertStep(AlarmCreationGuideStep.CreateAlarm)
            compose.runOnIdle {
                assertEquals(AlarmCreationGuideSurface.Editor, fixture.surface)
                assertEquals(0, fixture.destinationClicks)
                assertEquals(0, fixture.soundClicks)
            }
        }
    }

    @Test
    fun `마지막 안내는 시작하기를 눌러야 종료하고 목적지가 없어도 알람을 저장하지 않는다`() {
        val fixture = Fixture(AlarmCreationGuideStep.CreateAlarm)
        launch(fixture).use {
            val original = fixture.setup
            assertStep(AlarmCreationGuideStep.CreateAlarm)
            compose.onNodeWithTag("guide-overlay").assertHasNoClickAction()
            compose.onNodeWithText("시작하기").assertIsEnabled().assertHasClickAction()
            compose.onNodeWithText("목적지를 설정해주세요").assertDoesNotExist()
            pressDimmedArea()
            compose.onNodeWithTag("guide-description").performTouchInput { click() }
            assertStep(AlarmCreationGuideStep.CreateAlarm)
            compose.runOnIdle {
                assertEquals(0, fixture.finishCalls)
                assertEquals(0, fixture.saveClicks)
                assertEquals(original, fixture.setup)
            }
            compose.onNodeWithText("시작하기").performTouchInput { click() }
            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            compose.onNodeWithText("시작하기").assertDoesNotExist()
            compose.onNodeWithText("목적지를 설정해주세요").assertIsDisplayed().assertIsNotEnabled()
            compose.runOnIdle {
                assertEquals(1, fixture.finishCalls)
                assertEquals(0, fixture.saveClicks)
                assertEquals(original, fixture.setup)
            }
        }
    }

    @Test
    fun `마지막 강조 영역 터치는 가이드만 닫고 이후 새 터치로만 알람을 저장한다`() {
        val fixture = Fixture(AlarmCreationGuideStep.CreateAlarm).apply {
            setup = setup.copy(destination = PreviewDestination)
        }
        launch(fixture).use {
            assertStep(AlarmCreationGuideStep.CreateAlarm)
            compose.onNodeWithTag("guide-target").performTouchInput { click(Offset(width * .15f, height / 2f)) }
            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(1, fixture.finishCalls)
                assertEquals(0, fixture.saveClicks)
                assertEquals(PreviewDestination, fixture.setup.destination)
            }
            compose.onNodeWithText("저장").performTouchInput { click() }
            compose.runOnIdle {
                assertEquals(1, fixture.saveClicks)
                assertTrue(fixture.setup.isSaveInProgress)
            }
        }
    }

    @Test
    fun `가이드 종료 뒤 시간과 요일과 반복 간격을 자유롭게 수정한다`() {
        val fixture = Fixture(AlarmCreationGuideStep.Time)
        launch(fixture).use {
            compose.onNodeWithTag("guide-skip").performTouchInput { click() }
            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            val originalTime = fixture.setup.time
            compose.onNodeWithContentDescription("시").performTouchInput { swipeUp() }
            compose.runOnIdle { assertNotEquals(originalTime, fixture.setup.time) }
            compose.onNodeWithText("월").performScrollTo().performTouchInput { click() }
            compose.runOnIdle { assertFalse("월" in fixture.setup.selectedDays) }
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
                .performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
            compose.runOnIdle {
                assertEquals(10, fixture.setup.limitMinutes)
                assertEquals(1, fixture.finishCalls)
            }
        }
    }

    @Test
    fun `알람 추가 안내를 건너뛰면 기존 화면을 다시 조작할 수 있다`() =
        assertSkipRestoresControls(AlarmCreationGuideStep.AddAlarm)

    @Test
    fun `시간 안내를 건너뛰면 기존 화면을 다시 조작할 수 있다`() =
        assertSkipRestoresControls(AlarmCreationGuideStep.Time)

    @Test
    fun `요일 안내를 건너뛰면 기존 화면을 다시 조작할 수 있다`() =
        assertSkipRestoresControls(AlarmCreationGuideStep.Weekdays)

    @Test
    fun `목적지 안내를 건너뛰면 기존 화면을 다시 조작할 수 있다`() =
        assertSkipRestoresControls(AlarmCreationGuideStep.Destination)

    @Test
    fun `반복 간격 안내를 건너뛰면 기존 화면을 다시 조작할 수 있다`() =
        assertSkipRestoresControls(AlarmCreationGuideStep.Interval)

    @Test
    fun `알람음 안내를 건너뛰면 기존 화면을 다시 조작할 수 있다`() =
        assertSkipRestoresControls(AlarmCreationGuideStep.Sound)

    @Test
    fun `저장 안내를 건너뛰면 기존 화면을 다시 조작할 수 있다`() =
        assertSkipRestoresControls(AlarmCreationGuideStep.CreateAlarm)

    @Test
    fun `가이드 종료 저장 중에는 반복 터치나 건너뛰기로 실제 설정을 실행하지 않는다`() {
        val fixture = Fixture(AlarmCreationGuideStep.CreateAlarm).apply {
            holdFinish = true
            setup = setup.copy(destination = PreviewDestination)
        }
        launch(fixture).use {
            pressTarget()
            repeat(3) { pressDimmedArea() }
            compose.onNodeWithTag("guide-skip").assertIsNotEnabled().performTouchInput { click() }
            compose.onNodeWithTag("guide-overlay").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(1, fixture.finishCalls)
                assertEquals(0, fixture.saveClicks)
                assertEquals(0, fixture.inputChanges)
            }
        }
    }

    @Test
    fun `가이드 종료 기록에 실패하면 기존 조작을 차단한 채 종료 저장만 재시도한다`() {
        val fixture = Fixture().apply { failNextFinish = true }
        launch(fixture).use {
            assertStep(AlarmCreationGuideStep.AddAlarm)
            compose.onNodeWithTag("guide-skip").performTouchInput { click() }
            compose.onNodeWithTag("guide-target").assertDoesNotExist()
            compose.onNodeWithText("가이드 종료를 저장하지 못했어요.").assertIsDisplayed()
            compose.onNodeWithText("다시 시도").assertIsDisplayed().performTouchInput { click() }
            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(2, fixture.finishCalls)
                assertEquals(0, fixture.addClicks)
                assertEquals(0, fixture.saveClicks)
                assertFalse(fixture.isPending)
            }
        }
    }

    @Test
    fun `라이트와 다크 테마에서 기존 화면 위에 일곱 단계 안내를 표시한다`() {
        captureGuideJourney(fontScale = 1f, compact = false)
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `작은 화면과 두 배 글자 크기에서도 강조 대상과 건너뛰기를 누르고 설명을 읽을 수 있다`() {
        captureGuideJourney(fontScale = 2f, compact = true)
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp")
    fun `작은 화면에서 설명을 드래그하면 안내를 넘기지 않고 스크롤한다`() {
        val fixture = Fixture(AlarmCreationGuideStep.Destination)
        launch(fixture, fontScale = 2f).use {
            val scrollArea = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))
            scrollArea.performTouchInput { swipeUp() }
            compose.runOnIdle {
                assertEquals(AlarmCreationGuideStep.Destination, fixture.viewModel.uiState.value.step)
                assertEquals(0, fixture.inputChanges)
            }
            assertTrue(scrollArea.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
            compose.onNodeWithTag("guide-description").performTouchInput { click() }
            assertStep(AlarmCreationGuideStep.Interval)
        }
    }

    private fun assertSkipRestoresControls(step: AlarmCreationGuideStep) {
        val fixture = Fixture(step)
        launch(fixture).use {
            assertStep(step)
            compose.onNodeWithTag("guide-skip").assertIsDisplayed().performTouchInput { click() }
            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            compose.runOnIdle {
                assertFalse(fixture.isPending)
                assertEquals(1, fixture.finishCalls)
            }
            if (step == AlarmCreationGuideStep.AddAlarm) {
                compose.onNodeWithContentDescription("알람 추가").performClick()
                compose.runOnIdle { assertEquals(1, fixture.addClicks) }
            } else if (step == AlarmCreationGuideStep.Sound) {
                compose.onNodeWithText("기본 알람음").assertIsDisplayed().performClick()
                compose.runOnIdle { assertEquals(1, fixture.soundClicks) }
            } else {
                compose.onNodeWithContentDescription("뒤로").performClick()
                compose.runOnIdle { assertEquals(1, fixture.backClicks) }
            }
            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
        }
    }

    private fun captureGuideJourney(fontScale: Float, compact: Boolean) {
        ThemeMode.entries.forEach { theme ->
            val fixture = Fixture()
            launch(fixture, theme, fontScale).use { content ->
                AlarmCreationGuideStep.entries.forEach { step ->
                    assertStep(step)
                    compose.waitUntil(timeoutMillis = 10_000) {
                        compose.onAllNodesWithTag("guide-arrow-loaded").fetchSemanticsNodes().isNotEmpty()
                    }
                    compose.onNodeWithTag("guide-skip").assertIsDisplayed()
                    val description = compose.onNodeWithTag("guide-description")
                    description.assertIsDisplayed().assertTextFits()
                    if (compact && step == AlarmCreationGuideStep.CreateAlarm) {
                        // The empty corner of a tall pill is outside the visible start button.
                        compose.onNodeWithTag("guide-target").performTouchInput {
                            click(Offset(height * .1f, height * .1f))
                        }
                        assertStep(AlarmCreationGuideStep.CreateAlarm)
                    }
                    val overlay = compose.onNodeWithTag("guide-overlay").fetchSemanticsNode().boundsInRoot
                    val target = compose.onNodeWithTag("guide-target").fetchSemanticsNode().boundsInRoot
                    assertTrue(target.left >= overlay.left && target.right <= overlay.right + 1f)
                    assertTrue(target.top >= overlay.top && target.bottom <= overlay.bottom + 1f)
                    compose.runOnIdle {
                        val prefix = if (compact) "alarm-guide-compact" else "alarm-guide"
                        content.captureLayout("$prefix-${theme.name.lowercase()}-${step.name.lowercase()}")
                    }
                    pressTarget()
                }
                compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
                compose.runOnIdle { assertEquals(1, fixture.finishCalls) }
            }
        }
    }

    private fun assertStep(step: AlarmCreationGuideStep) {
        compose.onNodeWithTag("guide-overlay").assertIsDisplayed()
        compose.onNodeWithTag("guide-target").assertIsDisplayed()
        compose.onNodeWithText(step.guideDescription).assertIsDisplayed()
        compose.onNodeWithTag("guide-next-hint").assertDoesNotExist()
        if (step == AlarmCreationGuideStep.CreateAlarm) {
            compose.onNodeWithText("시작하기").assertIsDisplayed().assertTextFits()
        }
    }

    private fun pressDimmedArea() {
        compose.onNodeWithTag("guide-overlay").performTouchInput { click(Offset(4f, height / 2f)) }
    }

    private fun pressTarget() {
        compose.onNodeWithTag("guide-target").performTouchInput { click() }
    }

    private fun launch(
        fixture: Fixture,
        theme: ThemeMode = ThemeMode.Dark,
        fontScale: Float = 1f,
    ): FontScaleTestContent = launchWithFontScale(fontScale) {
        RingoutTheme(themeMode = theme) {
            AlarmCreationGuideRoute(
                viewModel = fixture.viewModel,
                isPending = fixture.isPending,
                surface = fixture.surface,
                canPresent = true,
                isAlarmSaving = fixture.setup.isSaveInProgress,
                isFinishing = false,
                finishFailed = fixture.finishFailed,
                onOpenEditor = fixture::openEditor,
                onFinish = fixture::finish,
            ) {
                FixtureScreen(fixture)
            }
        }
    }

    @Composable
    private fun FixtureScreen(fixture: Fixture) {
        when (fixture.surface) {
            AlarmCreationGuideSurface.Home -> HomeScreen(
                uiState = HomeUiState(isLoading = false),
                onAddAlarm = fixture::openEditor,
                onAlarmClick = {},
                onAlarmEnabledChange = { _, _ -> },
                onAlarmDelete = {},
            )
            AlarmCreationGuideSurface.Editor -> AlarmSetupScreen(
                uiState = fixture.setup,
                onAmPmChange = {
                    fixture.inputChanges++
                    fixture.setup = fixture.setup.copy(
                        time = fixture.setup.time.toAlarmTimePickerValue().copy(isAm = it).to24HourString(),
                    )
                },
                onHourChange = {
                    fixture.inputChanges++
                    fixture.setup = fixture.setup.copy(
                        time = fixture.setup.time.toAlarmTimePickerValue().copy(hour = it).to24HourString(),
                    )
                },
                onMinuteChange = {
                    fixture.inputChanges++
                    fixture.setup = fixture.setup.copy(
                        time = fixture.setup.time.toAlarmTimePickerValue().copy(minute = it).to24HourString(),
                    )
                },
                onDayClick = { day ->
                    fixture.inputChanges++
                    fixture.setup = fixture.setup.copy(
                        selectedDays = if (day in fixture.setup.selectedDays) {
                            fixture.setup.selectedDays - day
                        } else {
                            fixture.setup.selectedDays + day
                        },
                    )
                },
                onLimitMinutesChange = {
                    fixture.inputChanges++
                    fixture.setup = fixture.setup.copy(limitMinutes = it)
                },
                onBackClick = { fixture.backClicks++ },
                onDestinationClick = {
                    fixture.destinationClicks++
                    fixture.surface = AlarmCreationGuideSurface.Other
                },
                onAlarmSoundClick = {
                    fixture.soundClicks++
                    fixture.surface = AlarmCreationGuideSurface.Other
                },
                onSaveClick = {
                    fixture.saveClicks++
                    fixture.setup = fixture.setup.copy(isScheduling = true, errorMessage = null)
                },
            )
            else -> Box(Modifier.fillMaxSize()) {
                Text("선택 화면")
            }
        }
    }

    private class Fixture(initialStep: AlarmCreationGuideStep = AlarmCreationGuideStep.AddAlarm) {
        val viewModel = AlarmCreationGuideViewModel()
        var isPending by mutableStateOf(true)
        var surface by mutableStateOf(AlarmCreationGuideSurface.Home)
        var setup by mutableStateOf(AlarmSetupUiState())
        var finishFailed by mutableStateOf(false)
        var failNextFinish = false
        var holdFinish = false
        var addClicks = 0
        var destinationClicks = 0
        var soundClicks = 0
        var saveClicks = 0
        var backClicks = 0
        var inputChanges = 0
        var finishCalls = 0

        init {
            viewModel.onContextChanged(true, surface, true)
            while (viewModel.uiState.value.step != initialStep) {
                val step = requireNotNull(viewModel.uiState.value.step)
                check(viewModel.onNext(step))
                if (step == AlarmCreationGuideStep.AddAlarm) {
                    surface = AlarmCreationGuideSurface.Editor
                    viewModel.onContextChanged(true, surface, true)
                }
            }
        }

        fun openEditor() {
            addClicks++
            surface = AlarmCreationGuideSurface.Editor
        }

        fun finish() {
            finishCalls++
            if (failNextFinish) {
                failNextFinish = false
                finishFailed = true
            } else {
                finishFailed = false
                if (!holdFinish) isPending = false
            }
        }
    }
}

private val PreviewDestination = DestinationSelection(
    name = "아침 운동 공원",
    address = "서울특별시 중구 세종대로 110",
    latitude = 37.5665851,
    longitude = 126.9782038,
)
