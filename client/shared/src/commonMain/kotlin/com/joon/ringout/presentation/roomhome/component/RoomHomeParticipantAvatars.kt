package com.joon.ringout.presentation.roomhome.component

import coil3.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomhome.roomHomeColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.room_member_default_avatar

@Composable
internal fun RoomHomeParticipantAvatars(
    count: Int,
    modifier: Modifier = Modifier,
    profileImageUrls: List<String?> = emptyList(),
    avatarSize: Dp = 19.dp,
) {
    val visibleCount = count.coerceIn(0, 3)
    if (visibleCount == 0) return

    Box(modifier = modifier.size(width = avatarSize + ((visibleCount - 1) * 9.5f).dp, height = avatarSize)) {
        repeat(visibleCount) { index ->
            val defaultAvatar = painterResource(Res.drawable.room_member_default_avatar)
            AsyncImage(
                model = profileImageUrls.getOrNull(index)?.takeIf(String::isNotBlank),
                placeholder = defaultAvatar,
                error = defaultAvatar,
                fallback = defaultAvatar,
                contentDescription = null,
                modifier = Modifier.offset(x = (index * 9.5f).dp).size(avatarSize).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Preview
@Composable
private fun RoomHomeParticipantAvatarsPreview() {
    RingoutTheme {
        RoomHomeParticipantAvatars(count = 3, modifier = Modifier.background(roomHomeColors().background))
    }
}
