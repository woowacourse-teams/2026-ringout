package com.joon.ringout.presentation.profilechange

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

internal const val MaxProfileImageBytes = 5L * 1024 * 1024

internal fun isProfileImageSizeTooLarge(fileSizeBytes: Long): Boolean =
    fileSizeBytes > MaxProfileImageBytes

internal sealed interface ProfileImagePickResult {
    data class Selected(val image: ImageBitmap) : ProfileImagePickResult
    data object TooLarge : ProfileImagePickResult
    data object Cancelled : ProfileImagePickResult
    data object Failure : ProfileImagePickResult
}

@Composable
internal expect fun rememberProfileImagePicker(
    onResult: (ProfileImagePickResult) -> Unit,
): () -> Unit
