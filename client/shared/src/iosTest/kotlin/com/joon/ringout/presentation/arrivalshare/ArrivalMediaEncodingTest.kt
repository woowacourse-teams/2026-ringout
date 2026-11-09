package com.joon.ringout.presentation.arrivalshare

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Image
import kotlin.test.*

class ArrivalMediaEncodingTest {
    @Test fun `카드 비트맵을 실제 1080 정사각형 PNG로 인코딩한다`() = runTest {
        val bytes = encodeArrivalPng(ImageBitmap(1080, 1080))
        assertContentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), bytes.take(8).toByteArray())
        val image = Image.makeFromEncoded(bytes)
        assertEquals(1080, image.width)
        assertEquals(1080, image.height)
        image.close()
    }
    @Test fun `카드 규격이 아닌 이미지는 내보내지 않는다`() = runTest {
        assertFailsWith<IllegalStateException> { encodeArrivalPng(ImageBitmap(400, 400)) }
    }
}
