package com.joon.ringout.presentation.destination

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    fun `저장 중에는 시스템과 상단 뒤로가기를 막고 저장이 끝나면 다시 허용한다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var isSaving by mutableStateOf(true)
        var backs = 0
        try {
            activity.get().setContent {
                RingoutTheme {
                    DestinationManagementScreen(
                        destination = SavedDestination(9, "헬스장", "서울 중구", 37.56, 126.97),
                        onBackClick = { backs++ },
                        onSave = {},
                        onDelete = {},
                        isSaving = isSaving,
                        mapContent = { Box(it) },
                    )
                }
            }
            compose.onNodeWithContentDescription("뒤로가기").performClick()
            compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
            compose.runOnIdle {
                assertEquals(0, backs)
                isSaving = false
            }
            compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
            compose.runOnIdle { assertEquals(1, backs) }
        } finally { activity.pause().stop().destroy() }
    }

    @Test
    fun `삭제 확인 중 시스템 뒤로가기는 삭제와 화면 이동 없이 확인창만 닫는다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var backs = 0
        var deletes = 0
        try {
            activity.get().setContent {
                RingoutTheme {
                    DestinationManagementScreen(
                        destination = SavedDestination(9, "헬스장", "서울 중구", 37.56, 126.97),
                        onBackClick = { backs++ },
                        onSave = {},
                        onDelete = { deletes++ },
                        mapContent = { Box(it) },
                    )
                }
            }
            compose.onNodeWithContentDescription("목적지 삭제").performClick()
            compose.onNodeWithText("목적지 삭제").assertIsDisplayed()
            compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("목적지 삭제").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(0, backs)
                assertEquals(0, deletes)
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
            compose.onNodeWithText("저장된 목적지 목록에서 삭제할까요?", substring = true).assertIsDisplayed()
            compose.onNodeWithText("이미 알람에 설정한 목적지는 유지됩니다.", substring = true).assertIsDisplayed()
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
