package com.joon.ringout.presentation.alarmsetup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.joon.ringout.presentation.alarmsetup.components.SaveAlarmButton
import com.joon.ringout.presentation.alarmsetup.components.WeekdaySelector
import com.joon.ringout.presentation.assertTextFits
import com.joon.ringout.presentation.destination.DestinationSelection
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
@Config(sdk = [35], qualifiers = "w320dp-h568dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlarmSetupLargeTextTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `큰 글자에서 요일은 여러 줄로 표시되고 일곱 요일 모두 선택할 수 있다`() {
        launchWithFontScale(2f) {
            var selected by remember { mutableStateOf(emptyList<String>()) }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                WeekdaySelector(selected, onDayClick = { selected = selected + it })
            }
        }.use {
            AlarmSetupWeekdays.forEach { day ->
                compose.onNodeWithText(day, useUnmergedTree = true).assertTextFits()
                compose.onNode(hasText(day) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
                    .performClick().assertIsOn()
            }
            compose.onNodeWithText("매일", useUnmergedTree = true).assertTextFits()
            val first = compose.onNodeWithText("월").fetchSemanticsNode().boundsInRoot
            val last = compose.onNodeWithText("일").fetchSemanticsNode().boundsInRoot
            assertTrue(last.top >= first.bottom)
        }
    }

    @Test
    fun `기본 글자에서는 작은 화면에서도 일곱 요일을 한 줄에 표시한다`() {
        launchWithFontScale(1f) {
            Column(Modifier.padding(20.dp)) { WeekdaySelector(emptyList(), {}) }
        }.use {
            val first = compose.onNodeWithText("월").fetchSemanticsNode().boundsInRoot
            AlarmSetupWeekdays.forEach { day ->
                compose.onNodeWithText(day, useUnmergedTree = true).assertTextFits()
                val bounds = compose.onNodeWithText(day).fetchSemanticsNode().boundsInRoot
                assertEquals(first.top, bounds.top, 1f)
            }
        }
    }

    @Test
    fun `큰 글자에서 저장 불가 및 저장 중 안내가 버튼 영역 안에 모두 표시된다`() {
        launchWithFontScale(2f) {
            Column(Modifier.padding(20.dp)) {
                SaveAlarmButton(false, {})
                SaveAlarmButton(false, {}, isInProgress = true)
            }
        }.use {
            listOf("목적지를 설정해주세요", "저장 중").forEach {
                compose.onNodeWithText(it, useUnmergedTree = true).assertTextFits()
                compose.onNodeWithText(it).assertIsDisplayed().assertIsNotEnabled()
            }
        }
    }

    @Test
    fun `큰 글자에서 설정 항목을 끝까지 스크롤하고 하단 저장 버튼으로 저장한다`() {
        verifyScreen(2f)
    }

    @Test
    fun `기본 글자에서 설정 항목 선택과 저장 동작을 유지한다`() {
        verifyScreen(1f)
    }

    @Test
    fun `중간 글자 크기에서 설정 안내와 저장 동작을 유지한다`() {
        verifyScreen(1.3f)
    }

    private fun verifyScreen(scale: Float) {
        val destination = "서울특별시 서초구 반포대로 58"
        val sound = "내가 선택한 아주 긴 이름의 알람 음악"
        var destinations = 0
        var sounds = 0
        var saved = 0
        var timeChanges = 0
        launchWithFontScale(scale) {
            AlarmSetupScreen(
                uiState = AlarmSetupUiState(
                    time = "23:59",
                    destination = DestinationSelection(destination, destination, 37.5, 127.0),
                    alarmSound = AlarmSoundSelection(sound, null),
                ),
                onAmPmChange = { timeChanges++ }, onHourChange = { timeChanges++ }, onMinuteChange = { timeChanges++ },
                onDayClick = {}, onLimitMinutesChange = {}, onBackClick = {},
                onDestinationClick = { destinations++ }, onAlarmSoundClick = { sounds++ }, onSaveClick = { saved++ },
            )
        }.use { content ->
            compose.onNodeWithContentDescription("시").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "11"))
            compose.onNodeWithContentDescription("분").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "59"))
            compose.onNodeWithText("저장").assertIsDisplayed()
            compose.runOnIdle { content.captureLayout("setup-top-$scale") }
            listOf("요일", "매일", "목적지", "알람을 끄고 이동할 곳을 선택해주세요", destination,
                "반복 알람 간격", "움직임이 없으면 5분마다 다시 울려요", "1분", "30분", "알람음", sound).forEach {
                compose.onNodeWithText(it, useUnmergedTree = true).assertTextFits()
            }
            compose.onNodeWithText(destination).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(sound).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { content.captureLayout("setup-bottom-$scale") }
            val soundBounds = compose.onNodeWithText(sound).fetchSemanticsNode().boundsInRoot
            val saveBounds = compose.onNodeWithText("저장").fetchSemanticsNode().boundsInRoot
            assertTrue(soundBounds.bottom <= saveBounds.top)
            compose.onNodeWithText("저장", useUnmergedTree = true).assertTextFits()
            compose.onNodeWithText("저장").assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(1, destinations)
                assertEquals(1, sounds)
                assertEquals(1, saved)
                assertEquals(0, timeChanges)
            }
        }
    }
}
