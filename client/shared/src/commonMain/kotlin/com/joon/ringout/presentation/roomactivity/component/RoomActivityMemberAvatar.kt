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
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomhome.roomHomeColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.room_member_default_avatar

@Composable
internal fun RoomActivityMemberAvatar(modifier: Modifier = Modifier, highlighted: Boolean = false) {
    Image(
        painter = painterResource(Res.drawable.room_member_default_avatar),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(38.dp).clip(CircleShape).then(
            if (highlighted) Modifier.border(2.dp, roomHomeColors().success, CircleShape) else Modifier,
        ),
    )
}

@Preview
@Composable
private fun RoomActivityMemberAvatarPreview() {
    RingoutTheme { RoomActivityMemberAvatar(highlighted = true) }
}
