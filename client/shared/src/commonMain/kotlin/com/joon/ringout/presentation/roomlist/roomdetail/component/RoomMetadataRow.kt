package com.joon.ringout.presentation.roomlist.roomdetail.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.social_room_member

@Composable
internal fun RoomMetadataRow(
    icon: DrawableResource,
    iconWidth: Dp,
    iconHeight: Dp,
    text: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(width = iconWidth, height = iconHeight),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RoomMetadataRowPreview() {
    RingoutTheme {
        RoomMetadataRow(
            icon = Res.drawable.social_room_member,
            iconWidth = 17.dp,
            iconHeight = 10.dp,
            text = "6명",
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
