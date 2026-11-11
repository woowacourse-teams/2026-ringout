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
    @Test fun `저장과 공유 버튼은 각 동작을 실행하고 작업 중에는 비활성화된다`() {
        var state by mutableStateOf(ArrivalShareUiState(false, date = "2026.11.03", time = "06:30"))
        var saveCount = 0
        var shareCount = 0
        launchWithFontScale(1f) {
            ArrivalShareScreen(state, {}, {}, {}, {}, onSave = { saveCount++ }, onShare = { shareCount++ })
        }.use {
            compose.onNodeWithContentDescription("이미지 저장").performClick()
            compose.onNodeWithContentDescription("이미지 공유").performClick()
            compose.runOnIdle {
                kotlin.test.assertEquals(1, saveCount)
                kotlin.test.assertEquals(1, shareCount)
                state = state.copy(busy = true)
            }
            compose.onNodeWithContentDescription("이미지 저장").assertIsNotEnabled()
            compose.onNodeWithContentDescription("이미지 공유").assertIsNotEnabled()
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp")
    fun `작은 화면에서 저장 성공과 실패 안내가 스크롤 없이 보인다`() {
        var state by mutableStateOf(ArrivalShareUiState(false, date = "2026.11.09", time = "06:30"))
        launchWithFontScale(1.3f) {
            ArrivalShareScreen(state, {}, {}, {}, {})
        }.use {
            compose.runOnIdle { state = state.copy(message = "사진을 저장했어요.") }
            compose.onNodeWithText("사진을 저장했어요.").assertIsDisplayed()
            compose.runOnIdle { state = state.copy(message = "사진 저장 권한이 필요해요. 설정에서 권한을 허용한 뒤 다시 시도해 주세요.") }
            compose.onNodeWithText("사진 저장 권한이 필요해요. 설정에서 권한을 허용한 뒤 다시 시도해 주세요.").assertIsDisplayed()
            compose.onNodeWithContentDescription("이미지 저장").assertIsDisplayed()
        }
    }
    @Test fun `토글 네 조합에서 선택한 문구만 표시하고 로고는 항상 표시한다`() {
        var state by mutableStateOf(ArrivalShareUiState(false, date = "2026.11.03", time = "06:30", arrivalTime = "06:53"))
        launchWithFontScale(1f) {
            ArrivalShareScreen(state, {}, { state = state.copy(showTime = it) }, { state = state.copy(showDate = it) }, {})
        }.use {
            compose.waitForIdle()
            compose.runOnIdle { it.captureLayout("arrival-editor") }
            compose.onNodeWithText("06:30").assertIsDisplayed()
            compose.onNodeWithText("2026.11.03").assertIsDisplayed()
            compose.onNodeWithText("06:53").assertDoesNotExist()
            compose.onNodeWithText("목적지 도착").assertDoesNotExist()
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
