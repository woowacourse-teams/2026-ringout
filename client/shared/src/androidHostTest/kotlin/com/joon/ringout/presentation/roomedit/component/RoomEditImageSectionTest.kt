package com.joon.ringout.presentation.roomedit.component

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.BitmapFactoryDecoder
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomEditImageSectionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun `선택한 사진은 가로를 채우고 세로 가로 정사각형 비율에 맞춰 높이를 바꾼다`() {
        val image = mutableStateOf(ImageBitmap(100, 200))
        val loading = mutableStateOf(false)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            activity.get().setContent {
                RingoutTheme {
                    Column(Modifier.width(240.dp).verticalScroll(rememberScrollState())) {
                        RoomEditImageSection(
                            room = PreviewRoom,
                            selectedImage = image.value,
                            onChangeClick = {},
                            isLoading = loading.value,
                            modifier = Modifier.testTag("image"),
                        )
                    }
                }
            }
            for ((width, height) in listOf(100 to 200, 300 to 100, 200 to 200)) {
                compose.runOnIdle { image.value = ImageBitmap(width, height) }
                assertImageSize(width.toFloat() / height)
                val bounds = compose.onNodeWithTag("image").fetchSemanticsNode().boundsInRoot
                compose.runOnIdle { loading.value = true }
                assertEquals(bounds, compose.onNodeWithTag("image").fetchSemanticsNode().boundsInRoot)
                compose.runOnIdle { loading.value = false }
            }
        } finally {
            activity.pause().stop().destroy()
        }
    }

    @Test
    @OptIn(DelicateCoilApi::class)
    fun `저장된 사진도 불러온 비율로 높이를 정하고 사진 주소가 바뀌면 새 비율을 반영한다`() {
        val portrait = imageFile(100, 200)
        val landscape = imageFile(300, 100)
        val room = mutableStateOf(PreviewRoom.copy(representativeImage = portrait.absolutePath))
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val previousImageLoader = SingletonImageLoader.get(activity.get())
        // Robolectric에서 지원하는 디코더로 실제 파일 로딩과 크기 전달을 검증한다.
        val imageLoader = ImageLoader.Builder(activity.get())
            .components { add(BitmapFactoryDecoder.Factory()) }
            .build()
        SingletonImageLoader.setUnsafe(imageLoader)
        try {
            activity.get().setContent {
                RingoutTheme {
                    Column(Modifier.width(240.dp).verticalScroll(rememberScrollState())) {
                        RoomEditImageSection(
                            room = room.value,
                            selectedImage = null,
                            onChangeClick = {},
                            modifier = Modifier.testTag("image"),
                        )
                    }
                }
            }
            awaitImageSize(0.5f)
            compose.runOnIdle { room.value = room.value.copy(representativeImage = landscape.absolutePath) }
            awaitImageSize(3f)
        } finally {
            activity.pause().stop().destroy()
            SingletonImageLoader.setUnsafe(previousImageLoader)
            imageLoader.shutdown()
            portrait.delete()
            landscape.delete()
        }
    }

    private fun awaitImageSize(aspectRatio: Float) {
        compose.waitUntil(timeoutMillis = 5_000) {
            val bounds = compose.onNodeWithTag("image").fetchSemanticsNode().boundsInRoot
            abs(bounds.height - bounds.width / aspectRatio) < 1f
        }
        assertImageSize(aspectRatio)
    }

    private fun assertImageSize(aspectRatio: Float) {
        val bounds = compose.onNodeWithTag("image").fetchSemanticsNode().boundsInRoot
        assertEquals(240f * compose.density.density, bounds.width, absoluteTolerance = 1f)
        assertEquals(bounds.width / aspectRatio, bounds.height, absoluteTolerance = 1f)
    }

    private fun imageFile(width: Int, height: Int): File {
        val file = File.createTempFile("room-image-", ".png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}

private val PreviewRoom = RoomUiModel(
    id = "7",
    representativeImage = null,
    name = "아침 러닝",
    description = "함께 달려요.",
    createdAt = "2026-09-15T09:00:00",
    activityDays = listOf("월", "수", "금"),
    activityTimeText = "오전 8:00",
    participantCount = 3,
    isJoined = true,
)
