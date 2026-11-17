package com.joon.ringout.presentation.mypage

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.mypage.model.MyPageAccountStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MyPageScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `계정 조회 중에는 로그인 유도 UI 없이 정책 링크를 유지한다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme(ThemeMode.Dark) {
                    MyPageScreenContent(
                        themeMode = ThemeMode.Dark,
                        appVersion = "1.0.0",
                        policies = DefaultMyPagePolicies,
                        accountStatus = MyPageAccountStatus.Loading,
                        onThemeModeChange = {},
                        onPolicyClick = {},
                    )
                }
            }

            compose.onNodeWithText("비로그인 상태입니다.").assertDoesNotExist()
            compose.onNodeWithText("계정 연동하기").assertDoesNotExist()
            compose.onNodeWithText("개인정보처리방침").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("이용약관").performScrollTo().assertIsDisplayed()
        } finally {
            activity.pause().stop().destroy()
        }
    }
}
