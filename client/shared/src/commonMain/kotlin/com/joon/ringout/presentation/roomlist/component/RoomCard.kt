package com.joon.ringout.presentation.roomlist.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ringoutColors
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

@Composable
fun RoomCard(
    room: RoomUiModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val roomCardColor = MaterialTheme.ringoutColors.roomCardBackground
    val metadataColor = MaterialTheme.ringoutColors.navigationInactiveContent

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = roomCardColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoomImage(room, Modifier.size(60.dp))
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${room.activityDaysText} / ${room.activityTimeText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = metadataColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${room.participantCount}명 참여중",
                    style = MaterialTheme.typography.bodySmall,
                    color = metadataColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(showBackground = false)
@Composable
private fun RoomCardPreview() {
    RingoutTheme {
        RoomCard(
            room = RoomUiModel(
                id = "preview-room",
                name = "퇴근 후 한강 러닝 모임",
                description = "모임 소개 문구 미리보기입니다.",
                createdAt = "2026-09-15T09:00:00",
                activityDays = listOf("월", "수", "금"),
                activityTimeText = "오후 7:30",
                participantCount = 12,
                isJoined = false,
            ),
            modifier = Modifier.padding(16.dp),
            onClick = {},
        )
    }
}
