package com.joon.ringout.presentation.update

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.update.component.MandatoryUpdateDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w383dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MandatoryUpdateDialogTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `강제 업데이트는 취소 버튼이 없고 뒤로가기와 바깥 터치로 닫히지 않는다`() {
        withContent(content = { MandatoryUpdateDialog(onUpdateClick = {}) }) {
            compose.onNodeWithText("취소").assertDoesNotExist()
            compose.onNodeWithText("업데이트 공지").assertIsDisplayed()
            compose.runOnIdle {
                val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
                dialog.onBackPressedDispatcher.onBackPressed()
                val now = SystemClock.uptimeMillis()
                val outsideTouch = MotionEvent.obtain(now, now, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
                try {
                    dialog.dispatchTouchEvent(outsideTouch)
                } finally {
                    outsideTouch.recycle()
                }
                assertTrue(dialog.isShowing)
            }
            compose.onNodeWithText("업데이트 공지").assertIsDisplayed()
            compose.onNodeWithText("스토어로 이동").assertIsDisplayed()
            captureDialog("mandatory-update")
        }
    }

    @Test
    fun `스토어 열기 실패 안내 후 재시도해도 업데이트 다이얼로그는 유지한다`() {
        var attempts = 0
        withContent(
            content = {
                var failed by remember { mutableStateOf(false) }
                MandatoryUpdateDialog(
                    onUpdateClick = {
                        attempts++
                        failed = attempts == 1
                    },
                    hasStoreOpenError = failed,
                )
            },
        ) {
            compose.onNodeWithText("스토어로 이동").performClick()
            compose.onNodeWithText(StoreErrorText).assertIsDisplayed()
            captureDialog("mandatory-update-store-error")

            compose.onNodeWithText("스토어로 이동").performClick()

            compose.runOnIdle { assertEquals(2, attempts) }
            compose.onNodeWithText(StoreErrorText).assertDoesNotExist()
            compose.onNodeWithText("업데이트 공지").assertIsDisplayed()
            compose.runOnIdle { assertTrue(ShadowDialog.getLatestDialog().isShowing) }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    fun `큰 글자와 작은 화면에서도 업데이트 안내를 읽고 스토어 버튼을 누를 수 있다`() {
        RuntimeEnvironment.setFontScale(2f)
        var attempts = 0
        try {
            withContent(content = { MandatoryUpdateDialog(onUpdateClick = { attempts++ }) }) {
                assertTextFits("업데이트 공지")
                assertTextFits("새로운 버전이 출시되었습니다.\n스토어에서 업데이트 해주세요.")
                compose.onNodeWithText("스토어로 이동").performScrollTo().assertIsDisplayed()
                assertTextFits("스토어로 이동")
                compose.onNodeWithText("스토어로 이동").performClick()
                compose.runOnIdle { assertEquals(1, attempts) }
                captureDialog("mandatory-update-large-text")
            }
        } finally {
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    private fun withContent(content: @Composable () -> Unit, verify: () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(android.R.style.Theme_Material_NoActionBar)
        controller.setup()
        controller.get().setContent {
            RingoutTheme(themeMode = ThemeMode.Light, content = content)
        }
        try {
            compose.waitForIdle()
            verify()
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun assertTextFits(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val result = results.single()
        assertFalse(text, result.multiParagraph.didExceedMaxLines)
        assertTrue(text, result.multiParagraph.height <= result.size.height + 1)
        for (line in 0 until result.lineCount) {
            assertFalse(text, result.isLineEllipsized(line))
            assertTrue(text, result.getLineRight(line) - result.getLineLeft(line) <= result.size.width + 1)
        }
    }

    private fun captureDialog(name: String) {
        compose.runOnIdle {
            val view = requireNotNull(ShadowDialog.getLatestDialog().window).decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val output = File("build/reports/mandatory-update/$name.png")
                requireNotNull(output.parentFile).mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private companion object {
        const val StoreErrorText = "스토어를 열 수 없습니다.\n스토어 또는 브라우저를 확인하고\n다시 시도해주세요."
    }
}
