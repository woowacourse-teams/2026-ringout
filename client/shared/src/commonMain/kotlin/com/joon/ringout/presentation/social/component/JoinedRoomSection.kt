package com.joon.ringout.presentation.social.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.social.model.SocialUiState

@Composable
internal fun JoinedRoomSection(
    uiState: SocialUiState,
    onRoomClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(
            items = uiState.joinedRooms,
            key = { room -> "joined-${room.id}" },
        ) { room ->
            JoinedRoomShortcut(
                room = room,
                onClick = { onRoomClick(room.id) },
            )
        }
    }
}

@Preview(name = "가입 모임 목록", widthDp = 402, heightDp = 220, showBackground = false)
@Composable
private fun JoinedRoomSectionPreview() {
    val mockData = SocialPreviewData()
    RingoutTheme {
        JoinedRoomSection(
            uiState = SocialUiState(
                allRooms = mockData.allRooms,
                joinedRooms = mockData.allRooms.filter { it.isJoined },
                isAuthenticated = true,
            ),
            onRoomClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}
