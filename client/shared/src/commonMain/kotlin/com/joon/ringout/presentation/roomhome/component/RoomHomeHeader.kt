package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomhome.roomHomeColors
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import com.joon.ringout.presentation.roomlist.roomdetail.component.RoomDetailPreviewRoom
import com.joon.ringout.presentation.roomlist.roomdetail.component.RoomMetadataRow
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.social_room_created_at
import ringout.shared.generated.resources.social_room_member

@Composable
internal fun RoomHomeHeader(
    room: RoomUiModel,
    modifier: Modifier = Modifier,
) {
    val colors = roomHomeColors()

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 20.dp)) {
        Text(
            text = room.name,
            modifier = Modifier.semantics { heading() },
            color = colors.content,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.height(8.dp))
        RoomMetadataRow(
            icon = Res.drawable.social_room_member,
            iconWidth = 16.dp,
            iconHeight = 16.dp,
            text = "${room.participantCount}명",
            color = colors.secondary,
        )
        RoomMetadataRow(
            icon = Res.drawable.social_room_created_at,
            iconWidth = 16.dp,
            iconHeight = 16.dp,
            text = room.createdAtText,
            color = colors.secondary,
        )
    }
}

@Preview(name = "모임 홈 제목 · 다크", widthDp = 402)
@Composable
private fun RoomHomeHeaderPreview() {
    RingoutTheme {
        RoomHomeHeader(
            room = RoomDetailPreviewRoom.copy(name = "아침 러닝가는 사람들"),
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 20.dp),
        )
    }
}

@Preview(name = "모임 홈 제목 · 라이트", widthDp = 402)
@Composable
private fun RoomHomeHeaderLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeHeader(
            room = RoomDetailPreviewRoom.copy(name = "아침 러닝가는 사람들"),
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 20.dp),
        )
    }
}
