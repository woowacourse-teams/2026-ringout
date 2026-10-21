package com.joon.ringout.presentation.common.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.joon.ringout.presentation.assertTextFits
import com.joon.ringout.presentation.launchWithFontScale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
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
        launchWithFontScale(2f) {
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
        launchWithFontScale(2f) {
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
        launchWithFontScale(1f) {
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
        compose.onNodeWithText(text, useUnmergedTree = true).assertTextFits()
    }
}
