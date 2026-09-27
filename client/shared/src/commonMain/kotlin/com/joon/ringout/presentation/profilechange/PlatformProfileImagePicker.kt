package com.joon.ringout.presentation.profilechange

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

internal sealed interface ProfileImagePickResult {
    data class Selected(val image: ImageBitmap) : ProfileImagePickResult
    data object Cancelled : ProfileImagePickResult
    data object Failure : ProfileImagePickResult
}

@Composable
internal expect fun rememberProfileImagePicker(
    onResult: (ProfileImagePickResult) -> Unit,
): () -> Unit
