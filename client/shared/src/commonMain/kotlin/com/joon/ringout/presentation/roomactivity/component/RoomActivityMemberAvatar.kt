package com.joon.ringout.presentation.roomactivity.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomhome.roomHomeColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.room_default_avartar_dark
import ringout.shared.generated.resources.room_default_avartar_light

@Composable
internal fun RoomActivityMemberAvatar(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    profileImageUrl: String? = null,
) {
    val defaultAvatar = when(LocalRingoutThemeMode.current) {
        ThemeMode.Dark -> Res.drawable.room_default_avartar_dark
        ThemeMode.Light -> Res.drawable.room_default_avartar_light
    }
    val avatarModifier = modifier.size(38.dp).clip(CircleShape).then(
        if (highlighted) Modifier.border(2.dp, roomHomeColors().success, CircleShape) else Modifier,
    )

    if (profileImageUrl.isNullOrBlank()) {
        Image(
            painter = painterResource(defaultAvatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier,
        )
    } else {
        AsyncImage(
            model = profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier,
            placeholder = painterResource(defaultAvatar),
            error = painterResource(defaultAvatar),
        )
    }
}

@Preview(name = "회원 아바타 · 다크")
@Composable
private fun RoomActivityMemberAvatarDarkPreview() {
    RingoutTheme(ThemeMode.Dark) { RoomActivityMemberAvatar(highlighted = true) }
}

@Preview(name = "회원 아바타 · 라이트")
@Composable
private fun RoomActivityMemberAvatarLightPreview() {
    RingoutTheme(ThemeMode.Light) { RoomActivityMemberAvatar(highlighted = true) }
}
