package com.joon.ringout.presentation.destination

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.destination.component.DestinationSaveDialogContent
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
@Config(sdk = [35], qualifiers = "w402dp-h947dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DestinationSaveFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `새 위치에서 저장 안함을 누르면 별명 입력이나 저장 없이 선택 결과만 전달한다`() = withMap { state, _ ->
        compose.onNodeWithText("이 위치를 목적지로 설정").performClick()
        compose.onNodeWithText("목적지 저장").assertIsDisplayed()
        compose.onNodeWithText("다음에 편하게 쓸 수 있도록\n목적지를 저장할까요?").assertIsDisplayed()
        compose.onNodeWithText("저장 안함").performClick()
        compose.onNodeWithText("목적지 저장").assertDoesNotExist()
        compose.onNodeWithContentDescription("목적지 별명").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(SelectedLocation), state.withoutSave)
            assertTrue(state.saves.isEmpty())
            assertTrue(state.savedSelections.isEmpty())
        }
    }

    @Test
    fun `저장하기는 기존 별명 입력창을 열고 별명을 입력해 저장한 시점에만 저장을 요청한다`() = withMap { state, _ ->
        compose.onNodeWithText("이 위치를 목적지로 설정").performClick()
        compose.onNodeWithText("저장하기").performClick()
        compose.onNodeWithText("목적지 저장").assertDoesNotExist()
        compose.onNodeWithText("목적지 별명 설정").assertIsDisplayed()
        compose.onNodeWithContentDescription("목적지 별명")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("저장").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(state.saves.isEmpty()) }
        compose.onNodeWithContentDescription("목적지 별명").performTextReplacement(" 우리 회사 ")
        compose.onNodeWithText("저장").performClick()
        compose.runOnIdle {
            assertEquals(listOf(SelectedLocation.toSavedDestination().copy(name = "우리 회사")), state.saves)
            assertTrue(state.withoutSave.isEmpty())
        }
    }

    @Test
    fun `별명 입력을 취소하고 다시 열면 이전 입력 없이 빈 값으로 시작한다`() = withMap { state, activity ->
        compose.onNodeWithText("이 위치를 목적지로 설정").performClick()
        compose.onNodeWithText("저장하기").performClick()
        compose.onNodeWithContentDescription("목적지 별명").performTextReplacement("우리 회사")
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("목적지 별명").assertDoesNotExist()
        compose.onNodeWithText("이 위치를 목적지로 설정").performClick()
        compose.onNodeWithText("저장하기").performClick()
        compose.onNodeWithContentDescription("목적지 별명")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("저장").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(state.saves.isEmpty()) }
    }

    @Test
    fun `이미 저장된 좌표는 저장 여부를 묻지 않고 기존 별명으로 선택한다`() =
        withMap(saved = listOf(SelectedLocation.toSavedDestination(9).copy(name = "회사"))) { state, _ ->
            compose.onNodeWithText("이 위치를 목적지로 설정").performClick()
            compose.onNodeWithText("목적지 저장").assertDoesNotExist()
            compose.onNodeWithContentDescription("목적지 별명").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(state.destinations, state.savedSelections)
                assertTrue(state.saves.isEmpty())
                assertTrue(state.withoutSave.isEmpty())
            }
        }

    @Test
    fun `저장 확인창에서 뒤로가면 지도에 머물고 다시 설정할 수 있다`() = withMap { state, activity ->
        compose.onNodeWithText("이 위치를 목적지로 설정").performClick()
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("목적지 저장").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, state.backs)
            assertTrue(state.withoutSave.isEmpty())
            assertTrue(state.saves.isEmpty())
        }
        compose.onNodeWithText("이 위치를 목적지로 설정").performClick()
        compose.onNodeWithText("저장하기").performClick()
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("목적지 별명 설정").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, state.backs) }
    }

    @Test
    fun `화면이 비활성화되면 미완료 저장 확인창을 닫는다`() = withMap { state, _ ->
        compose.onNodeWithText("이 위치를 목적지로 설정").performClick()
        compose.runOnIdle { state.active.value = false }
        compose.onNodeWithText("목적지 저장").assertDoesNotExist()
        compose.runOnIdle { state.active.value = true }
        compose.onNodeWithText("목적지 저장").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(state.saves.isEmpty())
            assertTrue(state.withoutSave.isEmpty())
        }
    }

    @Test
    fun `저장 확인 컴포넌트의 문구와 두 버튼을 표시한다`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DestinationSaveDialogContent({}, {})
                    }
                }
            }
            compose.onNodeWithText("목적지 저장").assertIsDisplayed()
            compose.onNodeWithText("저장 안함").assertIsDisplayed()
            compose.onNodeWithText("저장하기").assertIsDisplayed()
            compose.runOnIdle {
                val view = activity.get().findViewById<View>(android.R.id.content)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File("build/reports/ui/destination-save-dialog.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            activity.pause().stop().destroy()
        }
    }

    private fun withMap(
        saved: List<SavedDestination> = emptyList(),
        verify: (Fixture, ComponentActivity) -> Unit,
    ) {
        val state = Fixture(saved)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    DestinationMapScreen(
                        initialSelection = SelectedLocation,
                        savedDestinations = state.destinations,
                        onBackClick = { state.backs++ },
                        onConfirmClick = { state.saves += it },
                        onSavedDestinationConfirmClick = { state.savedSelections += it },
                        onConfirmWithoutSave = { state.withoutSave += it },
                        isDestinationActionEnabled = state.active.value,
                        mapContent = { Box(it) },
                    )
                }
            }
            verify(state, activity.get())
        } finally {
            activity.pause().stop().destroy()
        }
    }

    private class Fixture(val destinations: List<SavedDestination>) {
        val active = mutableStateOf(true)
        val saves = mutableListOf<SavedDestination>()
        val savedSelections = mutableListOf<SavedDestination>()
        val withoutSave = mutableListOf<DestinationSelection>()
        var backs = 0
    }
}

private val SelectedLocation = DestinationSelection("검색한 장소", "대한민국 서울 중구 세종대로 110", 37.5665, 126.978)
