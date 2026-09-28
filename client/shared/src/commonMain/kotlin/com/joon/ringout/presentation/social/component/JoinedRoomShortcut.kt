package com.joon.ringout.presentation.social.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.social.model.RoomUiModel

@Composable
fun JoinedRoomShortcut(
    room: RoomUiModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .width(78.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoomImage(room, Modifier.size(60.dp))
        Text(
            text = room.name,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = false)
@Composable
private fun JoinedRoomShortcutPreview() {
    RingoutTheme {
        JoinedRoomShortcut(
            room = RoomUiModel(
                id = "preview-room",
                name = "아침 러닝가는 사람들",
                activityDays = listOf("토", "일"),
                activityTimeText = "오전 8:00",
                participantCount = 8,
                isJoined = true,
            ),
            modifier = Modifier.padding(16.dp),
            onClick = {},
        )
    }
}
