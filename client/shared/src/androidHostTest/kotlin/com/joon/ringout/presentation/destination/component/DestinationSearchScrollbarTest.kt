package com.joon.ringout.presentation.destination.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DestinationSearchScrollbarTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `다크 모드에서도 흰 결과창의 스크롤바가 보이고 스크롤 위치를 따라 이동한다`() {
        verifyScrollbar(ThemeMode.Dark, itemCount = 15)
    }

    @Test
    fun `라이트 모드에서도 흰 결과창의 스크롤바가 보이고 스크롤 위치를 따라 이동한다`() {
        verifyScrollbar(ThemeMode.Light, itemCount = 15)
    }

    @Test
    fun `결과가 한 화면에 들어오면 스크롤바를 그리지 않는다`() {
        verifyScrollbar(ThemeMode.Dark, itemCount = 1)
    }

    private fun captureScrollbar(activity: ComponentActivity): ImageBitmap {
        val bounds = compose.onNodeWithTag("scrollbar").fetchSemanticsNode().boundsInWindow
        return compose.runOnIdle {
            val view = activity.findViewById<View>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            Bitmap.createBitmap(
                bitmap, bounds.left.toInt(), bounds.top.toInt(),
                bounds.width.toInt(), bounds.height.toInt(),
            ).asImageBitmap()
        }
    }

    private fun verifyScrollbar(themeMode: ThemeMode, itemCount: Int) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme(themeMode = themeMode) {
                    val listState = rememberLazyListState()
                    Box(Modifier.size(width = 240.dp, height = 280.dp).background(Color.White)) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("results")) {
                            items(itemCount) { index ->
                                Text("검색 결과 $index", modifier = Modifier.height(56.dp))
                            }
                        }
                        listState.scrollIndicatorState?.let { state ->
                            DestinationSearchScrollbar(
                                state = state,
                                modifier = Modifier.align(Alignment.CenterEnd).testTag("scrollbar"),
                            )
                        }
                    }
                }
            }
            val initial = captureScrollbar(activity.get()).toPixelMap()
            val x = initial.width / 2
            if (itemCount == 1) {
                for (y in 0 until initial.height) {
                    assertTrue(initial[x, y].luminance() > 0.99f, "스크롤할 수 없으면 배경만 보여야 한다")
                }
                return
            }

            val top = initial.height / 10
            val bottom = initial.height * 9 / 10
            val thumb = initial[x, top]
            val track = initial[x, bottom]
            assertTrue(1.05f / (thumb.luminance() + 0.05f) >= 3f, "흰 배경에서 스크롤 손잡이가 보여야 한다")
            assertTrue(thumb.luminance() < track.luminance(), "손잡이는 트랙보다 진해야 한다")

            compose.onNodeWithTag("results").performScrollToIndex(itemCount - 1)
            val scrolled = captureScrollbar(activity.get()).toPixelMap()
            assertTrue(scrolled[x, bottom].luminance() < scrolled[x, top].luminance(), "마지막 결과에서 손잡이가 아래로 이동해야 한다")
        } finally {
            activity.pause().stop().destroy()
        }
    }
}
