package com.joon.ringout.presentation.roomlist.roomdetail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import com.joon.ringout.ringoutColors
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.social_room_activity_days
import ringout.shared.generated.resources.social_room_activity_time
import ringout.shared.generated.resources.social_room_created_at
import ringout.shared.generated.resources.social_room_member

@Composable
internal fun RoomDetailInformation(room: RoomUiModel) {
    val metadataColor = MaterialTheme.ringoutColors.navigationInactiveContent
    val textColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 24.dp),
    ) {
        Text(
            text = room.name,
            color = textColor,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.height(4.dp))
        RoomMetadataRow(
            icon = Res.drawable.social_room_member,
            iconWidth = 17.dp,
            iconHeight = 10.dp,
            text = "${room.participantCount}명",
            color = metadataColor,
        )
        Spacer(Modifier.height(3.dp))
        RoomMetadataRow(
            icon = Res.drawable.social_room_created_at,
            iconWidth = 13.dp,
            iconHeight = 13.dp,
            text = room.createdAtText,
            color = metadataColor,
        )

        Spacer(Modifier.height(38.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            RoomActivitySummary(
                icon = Res.drawable.social_room_activity_days,
                text = room.activityDaysText,
                color = accentColor,
            )
            RoomActivitySummary(
                icon = Res.drawable.social_room_activity_time,
                text = room.activityTimeText,
                color = accentColor,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = room.description.ifBlank { "모임 소개가 아직 없어요." },
            color = textColor,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
        )
    }
}

@Preview(widthDp = 402, showBackground = true)
@Composable
private fun RoomDetailInformationPreview() {
    RingoutTheme {
        RoomDetailInformation(room = RoomDetailPreviewRoom)
    }
}

@Preview(name = "소개 없음", widthDp = 402, showBackground = true)
@Composable
private fun RoomDetailInformationWithoutDescriptionPreview() {
    RingoutTheme {
        RoomDetailInformation(room = RoomDetailPreviewRoom.copy(description = ""))
    }
}
