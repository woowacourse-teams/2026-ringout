package com.joon.ringout.presentation.roommembermanagement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.joon.ringout.domain.auth.AuthSessionState

/** 모임 메인 화면이 연결될 때 사용할 UI 진입점. 관리 전용 API에서 회원을 조회한다. */
@Composable
internal fun RoomMemberManagementRoute(
    viewModel: RoomMemberManagementViewModel,
    roomId: String,
    authSessionState: AuthSessionState,
    sessionIdentity: Any?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routeToken = remember(viewModel, roomId) { Any() }
    LaunchedEffect(viewModel, roomId, routeToken, authSessionState, sessionIdentity) {
        viewModel.onRouteVisible(roomId, authSessionState, sessionIdentity, routeToken)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val handleBack = { if (!uiState.isRemoving) onBackClick() }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = handleBack,
    )
    RoomMemberManagementScreen(
        uiState = uiState,
        onBackClick = handleBack,
        onRemoveMemberClick = viewModel::onRemoveMemberClick,
        onDismissRemove = viewModel::onDismissRemove,
        onConfirmRemove = viewModel::onConfirmRemove,
        modifier = modifier,
        onRetry = viewModel::onRetry,
    )
}
