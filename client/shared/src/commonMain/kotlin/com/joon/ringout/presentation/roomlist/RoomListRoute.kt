package com.joon.ringout.presentation.roomlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.common.component.ConfirmationDialog

@Composable
internal fun RoomListRoute(
    viewModel: RoomListViewModel,
    authSessionState: AuthSessionState,
    onCreateRoom: () -> Unit,
    onLoginClick: () -> Unit,
    onRoomClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLoginDialogVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel, authSessionState) {
        viewModel.onRouteVisible(authSessionState)
    }

    RoomListScreen(
        uiState = viewModel.uiState,
        onCreateRoom = {
            if (authSessionState == AuthSessionState.Authenticated) {
                onCreateRoom()
            } else {
                isLoginDialogVisible = true
            }
        },
        onRoomClick = onRoomClick,
        onRetryRooms = viewModel::onRetryRooms,
        modifier = modifier,
    )

    if (isLoginDialogVisible) {
        ConfirmationDialog(
            title = "로그인이 필요해요",
            description = "모임 기능을 사용하려면 로그인이 필요해요.\n지금 로그인을 하러 가볼까요?",
            confirmLabel = "로그인 하러 가기",
            confirmColor = MaterialTheme.colorScheme.primary,
            onDismiss = { isLoginDialogVisible = false },
            onConfirm = {
                isLoginDialogVisible = false
                onLoginClick()
            },
            cancelLabel = "지금은 안해요",
        )
    }
}
