package com.joon.ringout.presentation.profilechange

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberProfileImagePicker(
    onResult: (ProfileImagePickResult) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val lifetime = remember { ProfileImagePickerLifetime() }
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(lifetime) {
        lifetime.active = true
        onDispose { lifetime.active = false }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (!lifetime.active) return@rememberLauncherForActivityResult
        if (uri == null) {
            currentOnResult.value(ProfileImagePickResult.Cancelled)
        } else {
            coroutineScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { decodePreviewImage(context, uri) }
                }.fold(
                    onSuccess = { ProfileImagePickResult.Selected(it) },
                    onFailure = { ProfileImagePickResult.Failure },
                )
                if (lifetime.active) currentOnResult.value(result)
            }
        }
    }

    return remember(pickerLauncher, lifetime) {
        {
            runCatching {
                pickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }.onFailure {
                if (lifetime.active) currentOnResult.value(ProfileImagePickResult.Failure)
            }
        }
    }
}

private class ProfileImagePickerLifetime(var active: Boolean = true)

private fun decodePreviewImage(context: Context, uri: Uri): ImageBitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: error("The selected image could not be opened")

    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a readable image" }

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: error("The selected image could not be decoded")

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
