package com.joon.ringout.presentation.social

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.joon.ringout.domain.auth.AuthSessionState

@Composable
internal fun SocialRoute(
    viewModel: SocialViewModel,
    authSessionState: AuthSessionState,
    onCreateRoom: () -> Unit,
    onRoomClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel, authSessionState) {
        viewModel.onRouteVisible(authSessionState)
    }

    SocialScreen(
        uiState = viewModel.uiState,
        onCreateRoom = onCreateRoom,
        onRoomClick = onRoomClick,
        onRetryRooms = viewModel::onRetryRooms,
        modifier = modifier,
    )
}
