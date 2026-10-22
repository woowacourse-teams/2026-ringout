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
import androidx.compose.ui.test.assertIsNotEnabled
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlarmCreationGuideTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `딤드 영역을 눌러도 동작하지 않고 강조한 대상을 눌러야 다음 설명으로 이동한다`() {
        val fixture = Fixture()
        launch(fixture).use {
            assertStep(AlarmCreationGuideStep.AddAlarm)
            compose.onNodeWithTag("guide-overlay").performTouchInput {
                click(Offset(4f, height / 2f))
            }
            compose.runOnIdle {
                assertEquals(0, fixture.addClicks)
                assertEquals(AlarmCreationGuideStep.AddAlarm, fixture.viewModel.uiState.value.step)
            }

            pressTarget()
            assertStep(AlarmCreationGuideStep.Time)
            compose.runOnIdle { assertEquals(1, fixture.addClicks) }
            val originalSettings = fixture.setup
            val originalInputChanges = fixture.inputChanges
            compose.onNodeWithTag("guide-overlay").performTouchInput {
                click(Offset(4f, height / 2f))
            }
            assertStep(AlarmCreationGuideStep.Time)

            pressTarget()
            assertStep(AlarmCreationGuideStep.Weekdays)
            pressTarget()
            assertStep(AlarmCreationGuideStep.Destination)
            compose.runOnIdle {
                assertEquals(originalSettings, fixture.setup)
                assertEquals(originalInputChanges, fixture.inputChanges)
                assertEquals(0, fixture.destinationClicks)
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
    fun `목적지와 알람음 선택 중에는 가이드를 숨기고 취소하거나 선택해 돌아오면 알맞은 단계를 이어간다`() {
        val fixture = Fixture(AlarmCreationGuideStep.Destination)
        launch(fixture).use {
            assertStep(AlarmCreationGuideStep.Destination)
            pressTarget()
            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            compose.onNodeWithText("목적지 선택 화면").assertIsDisplayed()
            compose.runOnIdle { fixture.returnFromDestination(selected = false) }
            assertStep(AlarmCreationGuideStep.Destination)

            pressTarget()
            compose.runOnIdle { fixture.returnFromDestination(selected = true) }
            assertStep(AlarmCreationGuideStep.Interval)
            val originalSettings = fixture.setup
            pressTarget()
            assertStep(AlarmCreationGuideStep.Sound)
            compose.runOnIdle { assertEquals(originalSettings, fixture.setup) }

            pressTarget()
            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            compose.onNodeWithText("알람음 선택 화면").assertIsDisplayed()
            compose.runOnIdle { fixture.returnFromSound(selected = false) }
            assertStep(AlarmCreationGuideStep.Sound)
            pressTarget()
            compose.runOnIdle { fixture.returnFromSound(selected = true) }
            assertStep(AlarmCreationGuideStep.CreateAlarm)
            compose.runOnIdle {
                assertEquals(2, fixture.destinationClicks)
                assertEquals(2, fixture.soundClicks)
                assertEquals(0, fixture.finishCalls)
            }
        }
    }

    @Test
    fun `알람 저장 중에는 중복 입력을 막고 저장 실패 뒤에는 같은 버튼으로 재시도할 수 있다`() {
        val fixture = Fixture(AlarmCreationGuideStep.CreateAlarm)
        launch(fixture).use {
            assertStep(AlarmCreationGuideStep.CreateAlarm)
            val destination = fixture.setup.destination
            compose.runOnIdle { fixture.setup = fixture.setup.copy(destination = null) }
            compose.onNodeWithTag("guide-target").assertIsNotEnabled().performTouchInput { click() }
            compose.runOnIdle {
                assertEquals(0, fixture.saveClicks)
                fixture.setup = fixture.setup.copy(destination = destination)
            }
            pressTarget()
            compose.onNodeWithTag("guide-target").assertIsNotEnabled().performTouchInput { click() }
            compose.runOnIdle {
                assertEquals(1, fixture.saveClicks)
                assertEquals(0, fixture.finishCalls)
                fixture.setup = fixture.setup.copy(isScheduling = false, errorMessage = "저장 실패")
            }
            assertStep(AlarmCreationGuideStep.CreateAlarm)
            pressTarget()
            compose.runOnIdle {
                assertEquals(2, fixture.saveClicks)
                fixture.completeAlarmSave()
            }
            compose.onNodeWithTag("guide-overlay").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(1, fixture.finishCalls)
                assertFalse(fixture.isPending)
            }
        }
    }

    @Test
    fun `가이드 종료 기록에 실패하면 기존 조작을 차단한 채 종료 저장만 재시도한다`() {
        val fixture = Fixture().apply { failNextFinish = true }
        launch(fixture).use {
            assertStep(AlarmCreationGuideStep.AddAlarm)
            compose.onNodeWithTag("guide-skip").performClick()
            compose.onNodeWithTag("guide-target").assertDoesNotExist()
            compose.onNodeWithText("가이드 종료를 저장하지 못했어요.").assertIsDisplayed()
            compose.onNodeWithText("다시 시도").assertIsDisplayed().performClick()
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

    private fun assertSkipRestoresControls(step: AlarmCreationGuideStep) {
        val fixture = Fixture(step)
        launch(fixture).use {
            assertStep(step)
            compose.onNodeWithTag("guide-skip").assertIsDisplayed().performClick()
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
                    if (compact) description.performScrollTo()
                    description.assertIsDisplayed().assertTextFits()
                    val overlay = compose.onNodeWithTag("guide-overlay").fetchSemanticsNode().boundsInRoot
                    val target = compose.onNodeWithTag("guide-target").fetchSemanticsNode().boundsInRoot
                    assertTrue(target.left >= overlay.left && target.right <= overlay.right + 1f)
                    assertTrue(target.top >= overlay.top && target.bottom <= overlay.bottom + 1f)
                    compose.runOnIdle {
                        val prefix = if (compact) "alarm-guide-compact" else "alarm-guide"
                        content.captureLayout("$prefix-${theme.name.lowercase()}-${step.name.lowercase()}")
                    }
                    pressTarget()
                    when (step) {
                        AlarmCreationGuideStep.Destination -> compose.runOnIdle { fixture.returnFromDestination(true) }
                        AlarmCreationGuideStep.Sound -> compose.runOnIdle { fixture.returnFromSound(true) }
                        AlarmCreationGuideStep.CreateAlarm -> compose.runOnIdle { fixture.completeAlarmSave() }
                        else -> Unit
                    }
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
                hasDestination = fixture.setup.canSave,
                isAlarmSaving = fixture.setup.isSaveInProgress,
                isFinishing = false,
                finishFailed = fixture.finishFailed,
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
                onAddAlarm = {
                    fixture.addClicks++
                    fixture.surface = AlarmCreationGuideSurface.Editor
                },
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
                    fixture.surface = AlarmCreationGuideSurface.Destination
                },
                onAlarmSoundClick = {
                    fixture.soundClicks++
                    fixture.surface = AlarmCreationGuideSurface.Sound
                },
                onSaveClick = {
                    fixture.saveClicks++
                    fixture.setup = fixture.setup.copy(isScheduling = true, errorMessage = null)
                },
            )
            else -> Box(Modifier.fillMaxSize()) {
                Text(if (fixture.surface == AlarmCreationGuideSurface.Destination) "목적지 선택 화면" else "알람음 선택 화면")
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
        var addClicks = 0
        var destinationClicks = 0
        var soundClicks = 0
        var saveClicks = 0
        var backClicks = 0
        var inputChanges = 0
        var finishCalls = 0

        init {
            viewModel.onContextChanged(true, AlarmCreationGuideSurface.Home, true, false)
            while (viewModel.uiState.value.step != initialStep) {
                val step = requireNotNull(viewModel.uiState.value.step)
                viewModel.onTargetPressed(step)
                when (step) {
                    AlarmCreationGuideStep.AddAlarm -> surface = AlarmCreationGuideSurface.Editor
                    AlarmCreationGuideStep.Destination -> returnFromDestination(selected = true)
                    AlarmCreationGuideStep.Sound -> returnFromSound(selected = true)
                    else -> Unit
                }
            }
        }

        fun returnFromDestination(selected: Boolean) {
            if (selected) {
                setup = setup.copy(
                    destination = DestinationSelection(
                        name = "아침 운동 공원",
                        address = "서울특별시 중구 세종대로 110",
                        latitude = 37.5665851,
                        longitude = 126.9782038,
                    ),
                )
            }
            viewModel.onDestinationSelectionFinished(setup.canSave)
            surface = AlarmCreationGuideSurface.Editor
        }

        fun returnFromSound(selected: Boolean) {
            viewModel.onSoundSelectionFinished(selected)
            surface = AlarmCreationGuideSurface.Editor
        }

        fun completeAlarmSave() {
            setup = setup.copy(isScheduling = false)
            if (viewModel.onAlarmCreated()) finish()
        }

        fun finish() {
            finishCalls++
            if (failNextFinish) {
                failNextFinish = false
                finishFailed = true
            } else {
                finishFailed = false
                isPending = false
            }
        }
    }
}
