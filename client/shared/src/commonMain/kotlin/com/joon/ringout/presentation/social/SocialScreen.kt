package com.joon.ringout.presentation.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.social.component.JoinedRoomSection
import com.joon.ringout.presentation.social.component.RoomListHeader
import com.joon.ringout.presentation.social.component.RoomSection
import com.joon.ringout.presentation.social.component.SocialPreviewData
import com.joon.ringout.presentation.social.model.SocialUiState

@Composable
fun SocialScreen(
    uiState: SocialUiState,
    modifier: Modifier = Modifier,
    onCreateRoom: () -> Unit,
    onRoomClick: (String) -> Unit,
    onRetryRooms: () -> Unit,
) {
    val showJoinedRooms = uiState.isAuthenticated && uiState.joinedRooms.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (LocalRingoutThemeMode.current == ThemeMode.Dark) {
                    Color(0xFF101113)
                } else {
                    MaterialTheme.colorScheme.background
                },
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp),
    ) {
        RoomListHeader(
            onCreateRoom = onCreateRoom,
            modifier = Modifier.offset(x = 10.dp),
        )

        Spacer(Modifier.height(16.dp))

        if (showJoinedRooms) {
            JoinedRoomSection(
                uiState = uiState,
                onRoomClick = onRoomClick,
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

private enum class SocialPreviewScenario {
    JoinedRooms,
    WithoutJoinedRooms,
    EmptyRooms,
    AllRoomsLoading,
    AllRoomsError,
}

@Composable
private fun SocialScreenPreviewContent(
    themeMode: ThemeMode,
    scenario: SocialPreviewScenario,
) {
    val mockData = SocialPreviewData()
    val uiState = when (scenario) {
        SocialPreviewScenario.JoinedRooms -> SocialUiState(
            allRooms = mockData.allRooms,
            joinedRooms = mockData.allRooms.filter { it.isJoined },
            isAuthenticated = true,
        )

        SocialPreviewScenario.WithoutJoinedRooms -> SocialUiState(
            allRooms = mockData.allRooms.map { it.copy(isJoined = false) },
        )

        SocialPreviewScenario.EmptyRooms -> SocialUiState()

        SocialPreviewScenario.AllRoomsLoading -> SocialUiState(
            isAuthenticated = true,
            isLoadingAllRooms = true,
        )

        SocialPreviewScenario.AllRoomsError -> SocialUiState(
            allRoomsErrorMessage = RoomListLoadErrorMessage,
            isAuthenticated = true,
        )
    }

    RingoutTheme(themeMode = themeMode) {
        SocialScreen(
            uiState = uiState,
            onCreateRoom = {},
            onRoomClick = {},
            onRetryRooms = {},
        )
    }
}

@Preview(name = "라이트 · 가입 모임 있음", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun SocialWithJoinedRoomsLightPreview() {
    SocialScreenPreviewContent(ThemeMode.Light, SocialPreviewScenario.JoinedRooms)
}

@Preview(name = "다크 · 가입 모임 있음", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun SocialWithJoinedRoomsDarkPreview() {
    SocialScreenPreviewContent(ThemeMode.Dark, SocialPreviewScenario.JoinedRooms)
}

@Preview(name = "라이트 · 가입 모임 없음", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun SocialWithoutJoinedRoomsLightPreview() {
    SocialScreenPreviewContent(ThemeMode.Light, SocialPreviewScenario.WithoutJoinedRooms)
}

@Preview(name = "다크 · 가입 모임 없음", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun SocialWithoutJoinedRoomsDarkPreview() {
    SocialScreenPreviewContent(ThemeMode.Dark, SocialPreviewScenario.WithoutJoinedRooms)
}

@Preview(name = "라이트 · 빈 모임 목록", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun SocialEmptyRoomsLightPreview() {
    SocialScreenPreviewContent(ThemeMode.Light, SocialPreviewScenario.EmptyRooms)
}

@Preview(name = "다크 · 빈 모임 목록", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun SocialEmptyRoomsDarkPreview() {
    SocialScreenPreviewContent(ThemeMode.Dark, SocialPreviewScenario.EmptyRooms)
}

@Preview(name = "라이트 · 모임 목록 로딩", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun SocialAllRoomsLoadingLightPreview() {
    SocialScreenPreviewContent(ThemeMode.Light, SocialPreviewScenario.AllRoomsLoading)
}

@Preview(name = "다크 · 모임 목록 로딩", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun SocialAllRoomsLoadingDarkPreview() {
    SocialScreenPreviewContent(ThemeMode.Dark, SocialPreviewScenario.AllRoomsLoading)
}

@Preview(name = "라이트 · 모임 목록 오류", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun SocialAllRoomsErrorLightPreview() {
    SocialScreenPreviewContent(ThemeMode.Light, SocialPreviewScenario.AllRoomsError)
}

@Preview(name = "다크 · 모임 목록 오류", widthDp = 402, heightDp = 941, showBackground = true)
@Composable
private fun SocialAllRoomsErrorDarkPreview() {
    SocialScreenPreviewContent(ThemeMode.Dark, SocialPreviewScenario.AllRoomsError)
}
