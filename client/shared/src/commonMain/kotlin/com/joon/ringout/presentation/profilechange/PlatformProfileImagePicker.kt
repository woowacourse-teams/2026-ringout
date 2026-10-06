package com.joon.ringout.presentation.profilechange

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.joon.ringout.domain.member.MaxMemberProfileImageBytes
import com.joon.ringout.domain.member.ProfileImageUpload

internal const val MaxProfileImageBytes = MaxMemberProfileImageBytes

internal fun isProfileImageSizeTooLarge(fileSizeBytes: Long): Boolean =
    fileSizeBytes > MaxProfileImageBytes

internal sealed interface ProfileImagePickResult {
    data class Selected(val image: ImageBitmap, val upload: ProfileImageUpload) : ProfileImagePickResult
    data object TooLarge : ProfileImagePickResult
    data object Cancelled : ProfileImagePickResult
    data object Failure : ProfileImagePickResult
}

@Composable
internal expect fun rememberProfileImagePicker(
    onResult: (ProfileImagePickResult) -> Unit,
): () -> Unit
