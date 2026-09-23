package com.joon.ringout.presentation.profilechange.component.profileimage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.profilechange.component.ProfileImageEditDarkIconResource
import com.joon.ringout.presentation.profilechange.component.ProfileImageEditLightIconResource
import com.joon.ringout.presentation.profilechange.component.ProfileImagePlaceholderResource
import com.joon.ringout.presentation.profilechange.component.profileChangeColors
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ProfileImageEditor(
    onProfileImageChangeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = profileChangeColors()
    val editIcon = if (LocalRingoutThemeMode.current == ThemeMode.Dark) {
        ProfileImageEditDarkIconResource
    } else {
        ProfileImageEditLightIconResource
    }

    Box(
        modifier = modifier.size(ProfileImageEditorSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(colors.profileImageBackground),
        )
        Image(
            painter = painterResource(ProfileImagePlaceholderResource),
            contentDescription = null,
            modifier = Modifier.size(ProfileImagePlaceholderSize),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(ProfileImageEditTouchSize)
                .clickable(
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
}

private val ProfileImageEditorSize = 140.dp
private val ProfileImagePlaceholderSize = 48.dp
private val ProfileImageEditTouchSize = 48.dp
private val ProfileImageEditIconSize = 27.dp

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
            ProfileImageEditor(onProfileImageChangeClick = {})
        }
    }
}
