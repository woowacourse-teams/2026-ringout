package com.joon.ringout.presentation.profilechange

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
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
import java.io.InputStream
import java.io.ByteArrayOutputStream
import com.joon.ringout.domain.member.ProfileImageUpload

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
                    withContext(Dispatchers.IO) {
                        if (isSelectedImageTooLarge(context, uri)) {
                            ProfileImagePickResult.TooLarge
                        } else {
                            readSelectedImage(context, uri)
                        }
                    }
                }.fold(
                    onSuccess = { it },
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

private fun isSelectedImageTooLarge(context: Context, uri: Uri): Boolean {
    val resolver = context.contentResolver
    val reportedSize = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use(Cursor::sizeBytes)
    }.getOrNull()
    val knownSize = reportedSize?.takeIf { it > 0L }
    if (knownSize != null) return isProfileImageSizeTooLarge(knownSize)

    val inputStream = resolver.openInputStream(uri)
        ?: error("The selected image could not be opened")
    return inputStream.use(::isProfileImageTooLarge)
}

private fun Cursor.sizeBytes(): Long? {
    val sizeColumn = getColumnIndex(OpenableColumns.SIZE)
    return if (sizeColumn >= 0 && moveToFirst()) getLong(sizeColumn) else null
}

internal fun isProfileImageTooLarge(inputStream: InputStream): Boolean {
    val buffer = ByteArray(ProfileImageSizeReadBufferBytes)
    var bytesRead = 0L

    while (bytesRead <= MaxProfileImageBytes) {
        val remainingThroughLimit = (MaxProfileImageBytes + 1 - bytesRead).toInt()
        val read = inputStream.read(buffer, 0, minOf(buffer.size, remainingThroughLimit))
        when {
            read < 0 -> return false
            read == 0 -> {
                if (inputStream.read() < 0) return false
                bytesRead += 1
            }
            else -> bytesRead += read
        }
        if (isProfileImageSizeTooLarge(bytesRead)) return true
    }

    return false
}

private fun decodePreviewImage(context: Context, uri: Uri): ImageBitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsInput = resolver.openInputStream(uri)
        ?: error("The selected image could not be opened")
    boundsInput.use { input -> BitmapFactory.decodeStream(input, null, bounds) }

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
private const val ProfileImageSizeReadBufferBytes = 8 * 1024

private fun readSelectedImage(context: Context, uri: Uri): ProfileImagePickResult {
    val input = context.contentResolver.openInputStream(uri)
        ?: error("The selected image could not be opened")
    val bytes = input.use(::readProfileImageBytes) ?: return ProfileImagePickResult.TooLarge
    val type = context.contentResolver.getType(uri)
        ?: BitmapFactory.Options().also { options ->
            options.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.outMimeType
        ?: error("The selected image type could not be read")
    val extension = type.substringAfter('/').replace("+xml", "")
    return ProfileImagePickResult.Selected(
        image = decodePreviewImage(context, uri),
        upload = ProfileImageUpload(bytes, type, "profile.$extension"),
    )
}

/** 보고된 파일 크기가 잘못되어도 제한을 초과해 계속 읽지 않는다. */
internal fun readProfileImageBytes(input: InputStream): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(ProfileImageSizeReadBufferBytes)
    while (output.size() <= MaxProfileImageBytes) {
        val read = input.read(buffer, 0, minOf(buffer.size, (MaxProfileImageBytes + 1 - output.size()).toInt()))
        when {
            read < 0 -> return output.toByteArray()
            read == 0 -> {
                val byte = input.read()
                if (byte < 0) return output.toByteArray()
                output.write(byte)
            }
            else -> output.write(buffer, 0, read)
        }
    }
    return null
}
