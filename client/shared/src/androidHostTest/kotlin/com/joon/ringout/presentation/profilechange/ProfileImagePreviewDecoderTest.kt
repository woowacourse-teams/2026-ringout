package com.joon.ringout.presentation.profilechange

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.exifinterface.media.ExifInterface
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileImagePreviewDecoderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `정방향 사진은 크기와 사분면 위치를 유지한다`() {
        val bytes = createImageBytes(orientation = ExifInterface.ORIENTATION_NORMAL)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 160, 80, listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW))
    }

    @Test
    fun `좌우 반전 방향의 사진은 사분면을 좌우로 뒤집는다`() {
        val bytes = createImageBytes(orientation = ExifInterface.ORIENTATION_FLIP_HORIZONTAL)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 160, 80, listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE))
    }

    @Test
    fun `백팔십 도 방향의 사진은 사분면을 백팔십 도 회전한다`() {
        val bytes = createImageBytes(orientation = ExifInterface.ORIENTATION_ROTATE_180)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 160, 80, listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED))
    }

    @Test
    fun `상하 반전 방향의 사진은 사분면을 상하로 뒤집는다`() {
        val bytes = createImageBytes(orientation = ExifInterface.ORIENTATION_FLIP_VERTICAL)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 160, 80, listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN))
    }

    @Test
    fun `전치 방향의 사진은 왼쪽 위와 오른쪽 아래를 유지하며 가로와 세로를 바꾼다`() {
        val bytes = createImageBytes(orientation = ExifInterface.ORIENTATION_TRANSPOSE)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 80, 160, listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW))
    }

    @Test
    fun `구십 도 방향의 사진은 시계 방향으로 회전하며 가로와 세로를 바꾼다`() {
        val bytes = createImageBytes(orientation = ExifInterface.ORIENTATION_ROTATE_90)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 80, 160, listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN))
    }

    @Test
    fun `역전치 방향의 사진은 오른쪽 위와 왼쪽 아래를 유지하며 가로와 세로를 바꾼다`() {
        val bytes = createImageBytes(orientation = ExifInterface.ORIENTATION_TRANSVERSE)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 80, 160, listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED))
    }

    @Test
    fun `이백칠십 도 방향의 사진은 반시계 방향으로 회전하며 가로와 세로를 바꾼다`() {
        val bytes = createImageBytes(orientation = ExifInterface.ORIENTATION_ROTATE_270)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 80, 160, listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE))
    }

    @Test
    fun `방향 정보가 없는 제이펙 사진은 원래 방향을 유지한다`() {
        val bytes = createImageBytes()

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 160, 80, listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW))
    }

    @Test
    fun `방향 정보가 없는 피엔지 사진도 미리보기를 생성한다`() {
        val bytes = createImageBytes(format = Bitmap.CompressFormat.PNG)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 160, 80, listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW))
    }

    @Test
    fun `방향 값이 정의되지 않은 사진은 원래 방향을 유지한다`() {
        val bytes = createImageBytes(orientation = ExifInterface.ORIENTATION_UNDEFINED)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 160, 80, listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW))
    }

    @Test
    fun `방향 값이 유효 범위를 벗어난 사진은 원래 방향을 유지한다`() {
        val bytes = createImageBytes(orientation = 9)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 160, 80, listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW))
    }

    @Test
    fun `이미지로 읽을 수 없는 바이트는 미리보기 생성을 거부한다`() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        assertFailsWith<IllegalStateException> { decodeProfileImagePreview(bytes) }
    }

    @Test
    fun `긴 변이 칠백육십팔 픽셀인 사진은 더 축소하지 않는다`() {
        val bytes = createImageBytes(width = 768, height = 384)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 768, 384, listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW))
    }

    @Test
    fun `큰 사진은 긴 변이 칠백육십팔 픽셀 이하가 되도록 축소한다`() {
        val bytes = createImageBytes(width = 1536, height = 768)

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 768, 384, listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW))
    }

    @Test
    fun `큰 사진을 구십 도 회전해도 축소 제한과 사분면 위치를 지킨다`() {
        val bytes = createImageBytes(
            width = 1536,
            height = 768,
            orientation = ExifInterface.ORIENTATION_ROTATE_90,
        )

        val preview = decodeProfileImagePreview(bytes).asAndroidBitmap()

        assertPreview(preview, 384, 768, listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN))
    }

    @Test
    fun `미리보기 방향을 보정해도 업로드용 원본 바이트는 변경하지 않는다`() {
        val bytes = createImageBytes(orientation = ExifInterface.ORIENTATION_ROTATE_90)
        val originalBytes = bytes.copyOf()

        decodeProfileImagePreview(bytes)

        assertContentEquals(originalBytes, bytes)
    }

    private fun createImageBytes(
        width: Int = 160,
        height: Int = 80,
        orientation: Int? = null,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    ): ByteArray {
        // 저장된 픽셀은 왼쪽 위부터 빨강, 초록, 파랑, 노랑 순서다.
        val pixels = IntArray(width * height) { index ->
            val isLeft = index % width < width / 2
            val isTop = index / width < height / 2
            when {
                isLeft && isTop -> Color.RED
                isTop -> Color.GREEN
                isLeft -> Color.BLUE
                else -> Color.YELLOW
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val file = temporaryFolder.newFile()
        try {
            file.outputStream().use { output ->
                check(bitmap.compress(format, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        if (orientation != null) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return file.readBytes()
    }

    private fun assertPreview(
        preview: Bitmap,
        expectedWidth: Int,
        expectedHeight: Int,
        expectedQuadrants: List<Int>,
    ) {
        assertEquals(expectedWidth, preview.width, "미리보기 너비")
        assertEquals(expectedHeight, preview.height, "미리보기 높이")
        val actualQuadrants = listOf(
            preview.getPixel(preview.width / 4, preview.height / 4),
            preview.getPixel(preview.width * 3 / 4, preview.height / 4),
            preview.getPixel(preview.width / 4, preview.height * 3 / 4),
            preview.getPixel(preview.width * 3 / 4, preview.height * 3 / 4),
        )
        expectedQuadrants.zip(actualQuadrants).forEachIndexed { index, (expected, actual) ->
            // 제이펙 압축의 색상 오차만 허용하고 사분면 중심의 실제 출력 픽셀을 비교한다.
            assertTrue(
                abs(Color.red(expected) - Color.red(actual)) <= 12 &&
                    abs(Color.green(expected) - Color.green(actual)) <= 12 &&
                    abs(Color.blue(expected) - Color.blue(actual)) <= 12,
                "사분면 $index 색상: expected=${Integer.toHexString(expected)}, actual=${Integer.toHexString(actual)}",
            )
        }
    }
}
