package com.joon.ringout.presentation.ringing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.joon.ringout.presentation.launchWithFontScale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w640dp-h360dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlarmRingingLandscapeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `가로 화면에서 위로 밀어 알람 끄기 버튼을 누르면 콜백이 한 번 실행된다`() {
        var dismissCount = 0
        launchWithFontScale(1f) {
            AlarmRingingScreen(
                currentTime = "07:00",
                dateText = "2026년 11월 19일 목요일",
                limitMinutes = 10,
                destinationName = "집",
                onDismissAndNavigateClick = { dismissCount++ },
            )
        }.use {
            compose.onRoot().performTouchInput { swipeUp() }
            compose.onNodeWithText("알람 끄고 목적지로 이동하기")
                .assertIsDisplayed()
                .performTouchInput { click() }
            compose.runOnIdle { assertEquals(1, dismissCount) }
        }
    }
}
