package com.joon.ringout.presentation.destination

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import com.joon.ringout.RingoutTheme
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h947dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DestinationManagementScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `검색 중 시스템 뒤로가기는 검색만 닫고 다음 뒤로가기에서 관리를 종료한다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var backs = 0
        try {
            activity.get().setContent {
                Box {
                    RingoutTheme {
                        DestinationManagementScreen(SavedDestination(9, "헬스장", "서울 중구", 37.56, 126.97), { backs++ }, {}, {}, mapContent = { Box(it) })
                    }
                }
            }
            compose.onNodeWithContentDescription("검색").performClick()
            compose.onNodeWithContentDescription("장소 또는 주소 검색").assertIsDisplayed()
            compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithContentDescription("장소 또는 주소 검색").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(0, backs)
                activity.get().onBackPressedDispatcher.onBackPressed()
            }
            compose.runOnIdle { assertEquals(1, backs) }
        } finally { activity.pause().stop().destroy() }
    }

    @Test
    fun `별명을 수정해 저장하면 기존 아이디와 위치를 유지한다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val destination = SavedDestination(9, "헬스장", "서울특별시 중구 세종대로 110", 37.5665, 126.978)
        var saved: SavedDestination? = null
        try {
            activity.get().setContent {
                Box {
                    RingoutTheme { DestinationManagementScreen(destination, {}, { saved = it }, {}, mapContent = { Box(it) }) }
                }
            }
            compose.onNodeWithText("목적지 수정").assertIsDisplayed()
            compose.onNodeWithContentDescription("목적지 별명").performTextReplacement(" 도서관 ")
            compose.onNodeWithText("저장").performClick()
            compose.runOnIdle {
                assertEquals(destination.copy(name = "도서관"), saved)
                saveScreenshot(activity.get().findViewById(android.R.id.content), "destination-management")
            }
            compose.onNodeWithContentDescription("목적지 별명").performTextReplacement("")
            compose.onNodeWithText("저장").assertIsNotEnabled()
        } finally { activity.pause().stop().destroy() }
    }

    @Test
    fun `삭제 버튼은 확인 다이얼로그를 열고 취소하면 삭제하지 않는다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var deletes = 0
        try {
            activity.get().setContent {
                Box {
                    RingoutTheme {
                        DestinationManagementScreen(SavedDestination(9, "헬스장", "서울 중구", 37.56, 126.97), {}, {}, { deletes++ }, mapContent = { Box(it) })
                    }
                }
            }
            compose.onNodeWithContentDescription("목적지 삭제").performClick()
            compose.onNodeWithText("정말 해당 목적지를 삭제하시겠습니까?").assertIsDisplayed()
            compose.runOnIdle { assertEquals(0, deletes) }
            compose.onNodeWithText("취소").performClick()
            compose.onNodeWithText("목적지 삭제").assertDoesNotExist()
            compose.runOnIdle { assertEquals(0, deletes) }
            compose.onNodeWithContentDescription("목적지 삭제").performClick()
            compose.onAllNodesWithText("삭제").onLast().performClick()
            compose.runOnIdle { assertEquals(1, deletes) }
        } finally { activity.pause().stop().destroy() }
    }

    private fun saveScreenshot(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/reports/ui/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
