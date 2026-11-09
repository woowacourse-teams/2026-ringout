package com.joon.ringout.presentation.arrival

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import ringout.shared.generated.resources.Res
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalResourceApi::class)
class ArrivalSeedAssetTest {
    @Test
    fun `씨앗 애니메이션 이미지를 아이오에스에서 해독하면 프레임과 투명 배경이 보존된다`() = runTest {
        val image = Res.readBytes("drawable/arrival_seed_animation.webp").decodeToImageBitmap()

        assertEquals(2_688, image.width)
        assertEquals(2_304, image.height)
        assertEquals(0f, image.alphaAt(0, 0), "첫 프레임의 배경은 투명해야 한다.")
        assertTrue(image.alphaAt(192, 192) > 0.9f, "첫 프레임의 씨앗이 표시되어야 한다.")
        assertTrue(image.alphaAt(6 * 384 + 192, 2 * 384 + 192) > 0.9f, "중간 프레임의 씨앗이 표시되어야 한다.")
        assertEquals(0f, image.alphaAt(6 * 384 + 192, 5 * 384 + 192), "사용하지 않는 마지막 칸은 투명해야 한다.")
    }

    private fun ImageBitmap.alphaAt(x: Int, y: Int): Float {
        val pixel = IntArray(1)
        readPixels(buffer = pixel, startX = x, startY = y, width = 1, height = 1)
        return Color(pixel.single()).alpha
    }
}
