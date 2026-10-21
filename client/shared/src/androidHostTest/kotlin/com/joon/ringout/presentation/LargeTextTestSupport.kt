package com.joon.ringout.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
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
import org.robolectric.android.controller.ActivityController
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal fun launchWithFontScale(scale: Float, content: @Composable () -> Unit): FontScaleTestContent {
    RuntimeEnvironment.setFontScale(scale)
    val controller = Robolectric.buildActivity(ComponentActivity::class.java)
    controller.get().setTheme(android.R.style.Theme_Material_NoActionBar)
    controller.setup()
    controller.get().setContent { RingoutTheme { content() } }
    return FontScaleTestContent(controller)
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

internal class FontScaleTestContent(private val controller: ActivityController<ComponentActivity>) : AutoCloseable {
    fun captureLayout(name: String) {
        val view = controller.get().window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/reports/large-text/$name.png")
        requireNotNull(output.parentFile).mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    override fun close() {
        controller.pause().stop().destroy()
        RuntimeEnvironment.setFontScale(1f)
    }
}
