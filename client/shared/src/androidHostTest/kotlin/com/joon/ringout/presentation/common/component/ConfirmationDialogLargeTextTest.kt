package com.joon.ringout.presentation.common.component

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.joon.ringout.RingoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h480dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConfirmationDialogLargeTextTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun `큰 글자와 작은 화면에서 긴 설명과 확인 버튼까지 스크롤하여 읽고 누른다`() {
        var confirmed = 0
        val title = "모임에서 탈퇴하시겠어요?"
        val description = "모임에서 활동한 모든 데이터가 삭제되며 다시 복구할 수 없습니다.\n".repeat(4)
        withContent(2f) {
            ConfirmationDialog(
                title = title, description = description,
                confirmLabel = "모임에서 탈퇴하기", confirmColor = MaterialTheme.colorScheme.primary,
                onDismiss = {}, onConfirm = { confirmed++ },
            )
        }.use {
            assertCompleteText(title)
            assertCompleteText(description)
            compose.onNodeWithText("모임에서 탈퇴하기").performScrollTo().assertIsDisplayed()
            assertCompleteText("모임에서 탈퇴하기")
            compose.onNodeWithText("모임에서 탈퇴하기").performClick()
            compose.runOnIdle { assertEquals(1, confirmed) }
        }
    }

    @Test
    fun `큰 글자의 긴 동작 이름은 버튼을 세로로 배치하여 보여준다`() {
        withContent(2f) {
            ConfirmationDialog(
                title = "모임 삭제", description = "삭제할까요?",
                confirmLabel = "회원 관리로 이동", confirmColor = MaterialTheme.colorScheme.primary,
                onDismiss = {}, onConfirm = {},
            )
        }.use {
            assertCompleteText("취소")
            assertCompleteText("회원 관리로 이동")
            val cancel = compose.onNodeWithText("취소").fetchSemanticsNode().boundsInRoot
            val confirm = compose.onNodeWithText("회원 관리로 이동").fetchSemanticsNode().boundsInRoot
            assertTrue(confirm.top >= cancel.bottom)
        }
    }

    @Test
    fun `기본 글자에서는 짧은 확인과 취소 버튼의 가로 배치를 유지한다`() {
        withContent(1f) {
            ConfirmationDialog(
                title = "알람 삭제", description = "삭제할까요?",
                confirmLabel = "삭제", confirmColor = MaterialTheme.colorScheme.primary,
                onDismiss = {}, onConfirm = {},
            )
        }.use {
            val cancel = compose.onNodeWithText("취소").fetchSemanticsNode().boundsInRoot
            val confirm = compose.onNodeWithText("삭제").fetchSemanticsNode().boundsInRoot
            assertEquals(cancel.top, confirm.top, 1f)
            assertTrue(confirm.left > cancel.right)
        }
    }

    private fun assertCompleteText(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.isNotEmpty())
        val result = results.single()
        // GetTextLayoutResult의 문단 너비는 Text 자체보다 넓은 부모 제약일 수 있다.
        assertFalse(result.multiParagraph.didExceedMaxLines, text)
        assertTrue(result.multiParagraph.height <= result.size.height + 1, text)
        for (line in 0 until result.lineCount) {
            assertFalse(result.isLineEllipsized(line), text)
            assertTrue(result.getLineRight(line) - result.getLineLeft(line) <= result.size.width + 1, text)
        }
    }

    private fun withContent(scale: Float, content: @Composable () -> Unit): AutoCloseable {
        RuntimeEnvironment.setFontScale(scale)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { RingoutTheme { content() } }
        compose.waitForIdle()
        return AutoCloseable {
            controller.pause().stop().destroy()
            RuntimeEnvironment.setFontScale(1f)
        }
    }
}
