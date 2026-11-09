package com.joon.ringout.presentation.arrivalshare

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.launchWithFontScale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArrivalShareScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun `토글 네 조합에서 선택한 문구만 표시하고 로고는 항상 표시한다`() {
        var state by mutableStateOf(ArrivalShareUiState(false, date = "2026.11.03", time = "06:30"))
        launchWithFontScale(1f) {
            ArrivalShareScreen(state, {}, { state = state.copy(showTime = it) }, { state = state.copy(showDate = it) }, {})
        }.use {
            compose.waitForIdle()
            compose.runOnIdle { it.captureLayout("arrival-editor") }
            compose.onNodeWithText("06:30").assertIsDisplayed()
            compose.onNodeWithText("2026.11.03").assertIsDisplayed()
            compose.onNodeWithText("기상 시간").performClick()
            compose.onNodeWithText("06:30").assertDoesNotExist()
            compose.onNodeWithText("날짜").performClick()
            compose.onNodeWithText("2026.11.03").assertDoesNotExist()
            compose.onNodeWithText("RINGOUT").assertIsDisplayed()
            compose.onNodeWithText("기상 시간").performClick()
            compose.onNodeWithText("06:30").assertIsDisplayed()
            compose.onNodeWithText("2026.11.03").assertDoesNotExist()
        }
    }
    @Test @Config(qualifiers = "w320dp-h640dp")
    fun `작은 화면과 큰 글씨에서도 날짜 조작과 뒤로 가기가 가능하다`() {
        var back = false
        launchWithFontScale(2f) { RingoutTheme(ThemeMode.Light) {
            ArrivalShareScreen(ArrivalShareUiState(false, date = "2026.11.09"), { back = true }, {}, {}, {})
        } }.use {
            compose.onNodeWithText("날짜").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("기상 시간").assertIsNotEnabled()
            compose.onNodeWithContentDescription("뒤로가기").performClick()
            kotlin.test.assertTrue(back)
        }
    }
}
