package com.joon.ringout.presentation

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.joon.ringout.RingoutTheme
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal fun launchWithFontScale(scale: Float, content: @Composable () -> Unit): AutoCloseable {
    RuntimeEnvironment.setFontScale(scale)
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    controller.get().setContent { RingoutTheme { content() } }
    return AutoCloseable {
        controller.pause().stop().destroy()
        RuntimeEnvironment.setFontScale(1f)
    }
}

internal fun SemanticsNodeInteraction.assertTextFits(): SemanticsNodeInteraction {
    val results = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
    assertTrue(results.isNotEmpty())
    val result = results.single()
    val text = result.layoutInput.text.text
    // GetTextLayoutResult의 문단 너비는 Text 자체보다 넓은 부모 제약일 수 있다.
    assertFalse(result.multiParagraph.didExceedMaxLines, text)
    assertTrue(result.multiParagraph.height <= result.size.height + 1, text)
    for (line in 0 until result.lineCount) {
        assertFalse(result.isLineEllipsized(line), text)
        assertTrue(result.getLineRight(line) - result.getLineLeft(line) <= result.size.width + 1, text)
    }
    return this
}
