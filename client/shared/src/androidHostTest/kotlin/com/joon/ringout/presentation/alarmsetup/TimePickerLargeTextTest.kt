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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.joon.ringout.presentation.alarmsetup.components.TimePickerCard
import com.joon.ringout.presentation.assertTextFits
import com.joon.ringout.presentation.launchWithFontScale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimePickerLargeTextTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `최대 글자에서 시와 분을 읽고 경계 값 다음 항목을 선택할 수 있다`() {
        verifyWheel(2f)
    }

    @Test
    fun `기본 글자에서도 시와 분의 순환 및 오전 오후 선택을 유지한다`() {
        verifyWheel(1f)
    }

    @Test
    fun `글자가 더 커져 시와 분을 세로 배치해도 각 숫자를 전부 표시한다`() {
        verifyWheel(3.2f)
    }

    private fun verifyWheel(scale: Float) {
        var resultHour = 12
        var resultMinute = 59
        var resultAm = true
        launchWithFontScale(scale) {
            var isAm by remember { mutableStateOf(true) }
            var hour by remember { mutableStateOf(12) }
            var minute by remember { mutableStateOf(59) }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                TimePickerCard(isAm, hour, minute,
                    { isAm = it; resultAm = it }, { hour = it; resultHour = it }, { minute = it; resultMinute = it })
            }
        }.use { content ->
            compose.runOnIdle { content.captureLayout("time-picker-$scale") }
            assertWheelText("시", "12")
            assertWheelText("분", "59")
            assertWheelText("오전 오후", "오전")
            compose.onNodeWithContentDescription("시").performScrollTo()
            compose.onNode(hasText("01") and hasAnyAncestor(hasContentDescription("시"))).performClick()
            compose.onNodeWithContentDescription("시").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "01"))
            compose.onNodeWithContentDescription("분").performScrollTo()
            compose.onNode(hasText("00") and hasAnyAncestor(hasContentDescription("분"))).performClick()
            compose.onNodeWithContentDescription("분").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "00"))
            compose.onNodeWithContentDescription("오전 오후").performScrollTo().performScrollToIndex(1)
            compose.onNodeWithContentDescription("오전 오후").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "오후"))
            assertWheelText("시", "01")
            assertWheelText("분", "00")
            compose.runOnIdle {
                assertEquals(1, resultHour)
                assertEquals(0, resultMinute)
                assertEquals(false, resultAm)
            }
        }
    }

    private fun assertWheelText(wheel: String, value: String) {
        compose.onNode(hasText(value) and hasAnyAncestor(hasContentDescription(wheel)), useUnmergedTree = true).assertTextFits()
    }
}
