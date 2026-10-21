package com.joon.ringout.presentation.profilechange

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal fun decodeProfileImagePreview(bytes: ByteArray): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a readable image" }

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: error("The selected image could not be decoded")

    return bitmap.asImageBitmap()
}

private fun calculateSampleSize(width: Int, height: Int): Int {
    val largestDimension = maxOf(width.toLong(), height.toLong())
    var sampleSize = 1
    while (largestDimension / sampleSize > MaxPreviewDimension && sampleSize < (1 shl 30)) {
        sampleSize *= 2
    }
    return sampleSize
}

private const val MaxPreviewDimension = 768
