package com.joon.ringout.presentation.roommembermanagement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joon.ringout.domain.auth.AuthSessionState

/** 모임 메인 화면이 연결될 때 사용할 UI 진입점. 서버 상세에서 회원을 다시 조회한다. */
@Composable
internal fun RoomMemberManagementRoute(
    viewModel: RoomMemberManagementViewModel,
    roomId: String,
    authSessionState: AuthSessionState,
    sessionIdentity: Any?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel, roomId, authSessionState, sessionIdentity) {
        viewModel.onRouteVisible(roomId, authSessionState, sessionIdentity)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RoomMemberManagementScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRemoveMemberClick = viewModel::onRemoveMemberClick,
        onDismissRemove = viewModel::onDismissRemove,
        onConfirmRemove = viewModel::onConfirmRemove,
        modifier = modifier,
        onRetry = viewModel::onRetry,
    )
}
