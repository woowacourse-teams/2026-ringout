package com.joon.ringout.presentation.profilechange

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface

internal fun decodeProfileImagePreview(bytes: ByteArray): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a readable image" }

    val exif = bytes.inputStream().use { ExifInterface(it) }
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: error("The selected image could not be decoded")

    val rotation = exif.rotationDegrees
    if (!exif.isFlipped && rotation == 0) return bitmap.asImageBitmap()

    val transform = Matrix().apply {
        // ExifInterface의 회전 각도는 수평 반전을 먼저 적용한 상태를 기준으로 한다.
        if (exif.isFlipped) postScale(-1f, 1f)
        postRotate(rotation.toFloat())
    }
    val orientedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
    if (orientedBitmap !== bitmap) bitmap.recycle()
    return orientedBitmap.asImageBitmap()
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
