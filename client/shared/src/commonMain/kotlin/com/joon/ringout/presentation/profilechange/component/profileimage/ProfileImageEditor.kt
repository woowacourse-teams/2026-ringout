package com.joon.ringout.presentation.profilechange.component.profileimage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.LocalRingoutThemeMode
import coil3.compose.AsyncImage
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.profilechange.component.ProfileImageEditDarkIconResource
import com.joon.ringout.presentation.profilechange.component.ProfileImageEditLightIconResource
import com.joon.ringout.presentation.profilechange.component.ProfileImagePlaceholderResource
import com.joon.ringout.presentation.profilechange.component.profileChangeColors
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ProfileImageEditor(
    profileImage: ImageBitmap?,
    errorMessage: String?,
    onProfileImageChangeClick: () -> Unit,
    modifier: Modifier = Modifier,
    profileImageUrl: String? = null,
    enabled: Boolean = true,
) {
    val colors = profileChangeColors()
    val editIcon = if (LocalRingoutThemeMode.current == ThemeMode.Dark) {
        ProfileImageEditDarkIconResource
    } else {
        ProfileImageEditLightIconResource
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(ProfileImageEditorSize),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(colors.profileImageBackground),
            ) {
                if (profileImage == null) {
                    Image(
                        painter = painterResource(ProfileImagePlaceholderResource),
                        contentDescription = null,
                        modifier = Modifier
                            .size(ProfileImagePlaceholderSize)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit,
                    )
                    if (!profileImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profileImageUrl,
                            contentDescription = "현재 프로필 사진",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                } else {
                    Image(
                        bitmap = profileImage,
                        contentDescription = "프로필 사진 미리보기",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(ProfileImageEditTouchSize)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = "프로필 이미지 변경",
                        onClick = onProfileImageChangeClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(editIcon),
                    contentDescription = null,
                    modifier = Modifier.size(ProfileImageEditIconSize),
                )
            }
        }
        errorMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(top = ProfileImageErrorSpacing),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val ProfileImageEditorSize = 140.dp
private val ProfileImagePlaceholderSize = 48.dp
private val ProfileImageEditTouchSize = 48.dp
private val ProfileImageEditIconSize = 27.dp
private val ProfileImageErrorSpacing = 8.dp

@Preview(name = "Profile image editor - Dark", widthDp = 200, heightDp = 200)
@Composable
private fun ProfileImageEditorDarkPreview() {
    ProfileImageEditorPreview(ThemeMode.Dark)
}

@Preview(name = "Profile image editor - Light", widthDp = 200, heightDp = 200)
@Composable
private fun ProfileImageEditorLightPreview() {
    ProfileImageEditorPreview(ThemeMode.Light)
}

@Composable
private fun ProfileImageEditorPreview(themeMode: ThemeMode) {
    RingoutTheme(themeMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(profileChangeColors().background),
            contentAlignment = Alignment.Center,
        ) {
            ProfileImageEditor(
                profileImage = null,
                errorMessage = null,
                onProfileImageChangeClick = {},
            )
        }
    }
}
