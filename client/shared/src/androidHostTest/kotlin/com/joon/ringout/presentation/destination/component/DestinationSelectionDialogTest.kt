package com.joon.ringout.presentation.destination.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.destination.SavedDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DestinationSelectionDialogTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `세 항목을 완전히 표시하고 더 많은 목적지는 고정 높이 안에서 스크롤한다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var selections = 0
        try {
            activity.get().setContent {
                RingoutTheme(themeMode = ThemeMode.Dark) {
                    DestinationSelectionDialogContent(
                        destinations = listOf("370-41", "헬스장", "러닝", "도서관").mapIndexed { index, name ->
                            SavedDestination(index + 1L, name, "대한민국 경기도 수원시 팔달구", 37.28, 127.01)
                        },
                        onDismissRequest = {}, onSelect = { selections++ }, onFindInMap = {},
                        modifier = Modifier.testTag("dialog"),
                    )
                }
            }
            val bounds = compose.onNodeWithTag("dialog").fetchSemanticsNode().boundsInWindow
            val listBounds = compose.onNodeWithContentDescription("저장된 목적지 목록").fetchSemanticsNode().boundsInWindow
            val thirdBounds = compose.onNodeWithText("러닝").fetchSemanticsNode().boundsInWindow
            assertTrue(thirdBounds.bottom <= listBounds.bottom)
            compose.onNodeWithText("370-41").assertIsDisplayed()
            compose.onNodeWithText("러닝").assertIsDisplayed()
            compose.onNodeWithContentDescription("저장된 목적지 스크롤").assertIsDisplayed()
            compose.runOnIdle {
                val view = activity.get().findViewById<View>(android.R.id.content)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val cropped = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
                assertEquals(android.graphics.Color.WHITE, cropped.getPixel(cropped.width / 2, 5))
                val output = File("build/reports/ui/destination-selection.png")
                output.parentFile?.mkdirs()
                output.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cropped.recycle()
                bitmap.recycle()
            }
            compose.onNodeWithContentDescription("저장된 목적지 스크롤").performTouchInput {
                swipe(Offset(center.x, height * 0.05f), Offset(center.x, height + 100f), 500)
            }
            compose.onNodeWithText("도서관").assertIsDisplayed()
            compose.onNodeWithText("지도에서 찾기").assertIsDisplayed()
            compose.onNodeWithContentDescription("저장된 목적지 스크롤").performTouchInput {
                swipe(Offset(center.x, height * 0.95f), Offset(center.x, -100f), 500)
            }
            compose.onNodeWithText("370-41").assertIsDisplayed()
            compose.runOnIdle { assertEquals(0, selections) }
            val selectButton = compose.onNodeWithContentDescription("370-41 선택")
            val scrollbarBounds = compose.onNodeWithContentDescription("저장된 목적지 스크롤")
                .fetchSemanticsNode().boundsInWindow
            assertTrue(selectButton.fetchSemanticsNode().boundsInWindow.right <= scrollbarBounds.left)
            selectButton.performTouchInput { click(center) }
            compose.runOnIdle { assertEquals(1, selections) }
            assertEquals(bounds, compose.onNodeWithTag("dialog").fetchSemanticsNode().boundsInWindow)
        } finally {
            activity.pause().stop().destroy()
        }
    }

    @Test
    fun `목적지가 세 개 이하이면 스크롤바를 표시하지 않는다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    DestinationSelectionDialogContent(
                        destinations = List(3) {
                            SavedDestination(it + 1L, "목적지 ${it + 1}", "서울특별시", 37.56, 126.97)
                        },
                        onDismissRequest = {}, onSelect = {}, onFindInMap = {},
                    )
                }
            }
            compose.onNodeWithText("목적지 3").assertIsDisplayed()
            compose.onNodeWithContentDescription("저장된 목적지 스크롤").assertDoesNotExist()
        } finally {
            activity.pause().stop().destroy()
        }
    }
}
