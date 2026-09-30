package com.joon.ringout.presentation.roommembermanagement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 모임 메인 화면이 연결될 때 사용할 UI 진입점. 현재 ViewModel의 변경은 메모리에만 남는다. */
@Composable
internal fun RoomMemberManagementRoute(
    viewModel: RoomMemberManagementViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RoomMemberManagementScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onRemoveMemberClick = viewModel::onRemoveMemberClick,
        onDismissRemove = viewModel::onDismissRemove,
        onConfirmRemove = viewModel::onConfirmRemove,
        modifier = modifier,
    )
}
