package com.joon.ringout.presentation.roomlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomlist.component.JoinedRoomSection
import com.joon.ringout.presentation.roomlist.component.RoomListHeader
import com.joon.ringout.presentation.roomlist.component.RoomSection
import com.joon.ringout.presentation.roomlist.component.RoomListPreviewData
import com.joon.ringout.presentation.roomlist.model.RoomListUiState
import com.joon.ringout.ringoutColors

@Composable
fun RoomListScreen(
    uiState: RoomListUiState,
    modifier: Modifier = Modifier,
    onCreateRoom: () -> Unit,
    onRoomClick: (String) -> Unit,
    onJoinedRoomClick: (String) -> Unit,
    onRetryRooms: () -> Unit,
) {
    val showJoinedRooms = uiState.isAuthenticated && uiState.joinedRooms.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.ringoutColors.mainScreenBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp),
    ) {
        RoomListHeader(
            onCreateRoom = onCreateRoom,
        )

        Spacer(Modifier.height(16.dp))

        if (showJoinedRooms) {
            JoinedRoomSection(
                uiState = uiState,
                onRoomClick = onJoinedRoomClick,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(18.dp))
        }

        RoomSection(
            uiState = uiState,
            onRoomClick = onRoomClick,
            onRetryRooms = onRetryRooms,
            modifier = Modifier.weight(1f),
        )
    }
}

private enum class RoomListPreviewScenario {
    JoinedRooms,
    WithoutJoinedRooms,
    EmptyRooms,
    AllRoomsLoading,
    AllRoomsError,
}

@Composable
private fun RoomListScreenPreviewContent(
    themeMode: ThemeMode,
    scenario: RoomListPreviewScenario,
) {
    val mockData = RoomListPreviewData()
    val uiState = when (scenario) {
        RoomListPreviewScenario.JoinedRooms -> RoomListUiState(
            allRooms = mockData.allRooms,
            joinedRooms = mockData.allRooms.filter { it.isJoined },
            isAuthenticated = true,
        )

        RoomListPreviewScenario.WithoutJoinedRooms -> RoomListUiState(
            allRooms = mockData.allRooms.map { it.copy(isJoined = false) },
        )

        RoomListPreviewScenario.EmptyRooms -> RoomListUiState()

        RoomListPreviewScenario.AllRoomsLoading -> RoomListUiState(
            isAuthenticated = true,
            isLoadingAllRooms = true,
        )

        RoomListPreviewScenario.AllRoomsError -> RoomListUiState(
            allRoomsErrorMessage = RoomListLoadErrorMessage,
            isAuthenticated = true,
        )
    }

    RingoutTheme(themeMode = themeMode) {
        RoomListScreen(
            uiState = uiState,
            onCreateRoom = {},
            onRoomClick = {},
            onJoinedRoomClick = {},
            onRetryRooms = {},
        )
    }
}

@Preview(name = "라이트 · 가입 모임 있음", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomListWithJoinedRoomsLightPreview() {
    RoomListScreenPreviewContent(ThemeMode.Light, RoomListPreviewScenario.JoinedRooms)
}

@Preview(name = "다크 · 가입 모임 있음", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomListWithJoinedRoomsDarkPreview() {
    RoomListScreenPreviewContent(ThemeMode.Dark, RoomListPreviewScenario.JoinedRooms)
}

@Preview(name = "라이트 · 가입 모임 없음", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomListWithoutJoinedRoomsLightPreview() {
    RoomListScreenPreviewContent(ThemeMode.Light, RoomListPreviewScenario.WithoutJoinedRooms)
}

@Preview(name = "다크 · 가입 모임 없음", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomListWithoutJoinedRoomsDarkPreview() {
    RoomListScreenPreviewContent(ThemeMode.Dark, RoomListPreviewScenario.WithoutJoinedRooms)
}

@Preview(name = "라이트 · 빈 모임 목록", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomListEmptyRoomsLightPreview() {
    RoomListScreenPreviewContent(ThemeMode.Light, RoomListPreviewScenario.EmptyRooms)
}

@Preview(name = "다크 · 빈 모임 목록", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomListEmptyRoomsDarkPreview() {
    RoomListScreenPreviewContent(ThemeMode.Dark, RoomListPreviewScenario.EmptyRooms)
}

@Preview(name = "라이트 · 모임 목록 로딩", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomListAllRoomsLoadingLightPreview() {
    RoomListScreenPreviewContent(ThemeMode.Light, RoomListPreviewScenario.AllRoomsLoading)
}

@Preview(name = "다크 · 모임 목록 로딩", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomListAllRoomsLoadingDarkPreview() {
    RoomListScreenPreviewContent(ThemeMode.Dark, RoomListPreviewScenario.AllRoomsLoading)
}

@Preview(name = "라이트 · 모임 목록 오류", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomListAllRoomsErrorLightPreview() {
    RoomListScreenPreviewContent(ThemeMode.Light, RoomListPreviewScenario.AllRoomsError)
}

@Preview(name = "다크 · 모임 목록 오류", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun RoomListAllRoomsErrorDarkPreview() {
    RoomListScreenPreviewContent(ThemeMode.Dark, RoomListPreviewScenario.AllRoomsError)
}
