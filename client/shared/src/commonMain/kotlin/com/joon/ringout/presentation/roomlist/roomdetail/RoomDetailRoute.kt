package com.joon.ringout.presentation.roomlist.roomdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

@Composable
internal fun RoomDetailRoute(
    room: RoomUiModel?,
    isLoading: Boolean,
    errorMessage: String?,
    authSessionState: AuthSessionState,
    onRouteVisible: (AuthSessionState) -> Unit,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onRetryRooms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(onRouteVisible, authSessionState) {
        onRouteVisible(authSessionState)
    }

    if (room != null) {
        RoomDetailScreen(
            room = room,
            authSessionState = authSessionState,
            onBackClick = onBackClick,
            onLoginClick = onLoginClick,
            onJoinRoom = onJoinRoom,
            modifier = modifier,
        )
    } else {
        RoomDetailStatusScreen(
            isLoading = isLoading,
            errorMessage = errorMessage,
            onBackClick = onBackClick,
            onRetry = onRetryRooms,
            modifier = modifier,
        )
    }
}
