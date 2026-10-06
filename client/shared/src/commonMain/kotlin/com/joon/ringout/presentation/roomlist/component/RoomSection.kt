package com.joon.ringout.presentation.roomlist.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.roomlist.model.RoomListUiState

@Composable
internal fun RoomSection(
    uiState: RoomListUiState,
    onRoomClick: (String) -> Unit,
    onRetryRooms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 128.dp),
    ) {
        if (uiState.allRooms.isNotEmpty() && uiState.isRefreshingAllRooms) {
            item(key = "all-rooms-refreshing") {
                RoomListMessage(
                    title = "모임 목록을 갱신하고 있어요",
                    isLoading = true,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        if (uiState.allRooms.isNotEmpty() && uiState.allRoomsRefreshErrorMessage != null) {
            item(key = "all-rooms-refresh-error") {
                RoomListMessage(
                    title = uiState.allRoomsRefreshErrorMessage,
                    onRetry = onRetryRooms,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        when {
            uiState.isLoadingAllRooms -> item(key = "all-rooms-loading") {
                RoomListMessage(
                    title = "모임을 불러오는 중이에요",
                    isLoading = true,
                )
            }

            uiState.allRoomsErrorMessage != null -> item(key = "all-rooms-error") {
                RoomListMessage(
                    title = uiState.allRoomsErrorMessage,
                    onRetry = onRetryRooms,
                )
            }

            uiState.allRooms.isEmpty() -> item(key = "all-rooms-empty") {
                RoomListMessage(
                    title = "아직 등록된 모임이 없어요",
                    description = "새 모임을 만들어 함께할 사람을 찾아보세요.",
                )
            }

            else -> items(
                items = uiState.allRooms,
                key = { room -> "all-${room.id}" },
            ) { room ->
                RoomCard(
                    room = room,
                    modifier = Modifier.padding(bottom = 10.dp),
                    onClick = { onRoomClick(room.id) },
                )
            }
        }
    }
}

@Preview(name = "전체 모임 목록", widthDp = 402, heightDp = 520, showBackground = true)
@Composable
private fun RoomSectionPreview() {
    RingoutTheme {
        RoomSection(
            uiState = RoomListUiState(allRooms = RoomListPreviewData().allRooms),
            onRoomClick = {},
            onRetryRooms = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .padding(20.dp),
        )
    }
}
