package com.joon.ringout.presentation.arrivalshare

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.exifinterface.media.ExifInterface
import com.joon.ringout.domain.arrival.*
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.launchWithFontScale
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w402dp-h941dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArrivalMediaTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun `이미지를 누르면 검정 글자를 내보내고 다시 누르면 원래 흰색 이미지로 돌아온다`() {
        val vm = ArrivalShareViewModel("color-export-test", MediaRepository(), format = { "2026.11.09" to "06:30" })
        var png: ByteArray? = null
        val media = ArrivalMediaActions({}, {}, { png = it; vm.finishMedia(ArrivalMediaResult.Done()) }, {})
        launchWithFontScale(1f) { ArrivalShareRoute(vm, {}, media) }.use { content ->
            compose.onNodeWithText("RINGOUT").assertIsDisplayed()
            fun export(name: String): Bitmap {
                compose.runOnIdle { png = null; content.captureLayout(name) }
                compose.onNodeWithContentDescription("이미지 저장").performClick()
                compose.waitUntil(15_000) { png != null || vm.uiState.value.message != null }
                assertNotNull(png, vm.uiState.value.message)
                return BitmapFactory.decodeByteArray(png!!, 0, png!!.size)
            }
            val white = export("arrival-white-text")
            compose.onNodeWithTag("arrival-share-card").performClick()
            val black = export("arrival-black-text")
            fun count(image: Bitmap, color: Int): Int {
                var count = 0
                for (y in 40 until 180) for (x in 40 until 650) if (image.getPixel(x, y) == color) count++
                return count
            }
            assertTrue(count(black, Color.BLACK) > count(white, Color.BLACK) + 200)
            assertTrue(count(white, Color.WHITE) > count(black, Color.WHITE) + 200)
            compose.onNodeWithTag("arrival-share-card").performClick()
            val restored = export("arrival-restored-white-text")
            assertTrue(white.sameAs(restored))
            white.recycle(); black.recycle(); restored.recycle()
        }
    }
    @Test fun `카드만 고해상도 PNG로 내보내고 편집 상태와 도착 확인을 유지한다`() {
        val repository = MediaRepository()
        val vm = ArrivalShareViewModel("media-export-test", repository, format = { "2026.11.09" to "06:30" })
        var png: ByteArray? = null
        val media = ArrivalMediaActions({}, {}, { png = it; vm.finishMedia(ArrivalMediaResult.Done()) }, {})
        launchWithFontScale(1f) { ArrivalShareRoute(vm, {}, media) }.use {
            compose.onNodeWithText("RINGOUT").assertIsDisplayed()
            compose.runOnIdle { it.captureLayout("arrival-before-export") }
            compose.onNodeWithContentDescription("이미지 저장").performClick()
            compose.waitUntil(15_000) { png != null || vm.uiState.value.message != null }
            assertNotNull(png, vm.uiState.value.message)
            val image = BitmapFactory.decodeByteArray(png!!, 0, png!!.size)
            assertEquals(1080, image.width); assertEquals(1080, image.height)
            // The card reaches every corner: editor background/toolbars are outside the capture.
            assertNotEquals(Color.rgb(15, 16, 18), image.getPixel(0, 0))
            assertNotEquals(Color.rgb(15, 16, 18), image.getPixel(1079, 1079))
            File("build/reports/large-text/arrival-export.png").apply { parentFile!!.mkdirs(); writeBytes(png!!) }
            assertEquals(0, repository.acknowledged)
            assertFalse(vm.uiState.value.busy)
            image.recycle()
        }
    }
    @Test fun `큰 사진은 크기를 줄여 보관하고 회전된 사진은 방향을 보정한다`() {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "large-camera.jpg")
        val bitmap = Bitmap.createBitmap(3200, 1600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
        ExifInterface(source).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val imported = importArrivalPhoto(context, Uri.fromFile(source))
        val normalized = BitmapFactory.decodeFile(imported)
        assertEquals(800, normalized.width); assertEquals(1600, normalized.height)
        assertTrue(File(imported).length() > 0)
        assertTrue(File(context.filesDir, "arrival-media").listFiles()!!.none { it.name.startsWith("source-") })
        normalized.recycle()
    }
}
private class MediaRepository : ArrivalResultRepository {
    var acknowledged = 0
    override suspend fun getResult(occurrenceId: String) = ArrivalResult(occurrenceId, MissionDate.parse("2026-11-09"), 2000, 1000, 1, true, null)
    override fun observeLatestUnacknowledgedResult() = flowOf<ArrivalResult?>(null)
    override suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long) { acknowledged++ }
    override suspend fun getGrowth() = ArrivalGrowth(1)
    override fun observeGrowth() = flowOf(ArrivalGrowth(1))
}
