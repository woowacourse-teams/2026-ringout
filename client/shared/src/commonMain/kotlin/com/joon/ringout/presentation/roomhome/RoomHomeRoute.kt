package com.joon.ringout.presentation.roomhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.missionhistory.MissionDate

/** 모임 상세를 조회하고 정보·기록 탭과 메뉴 동작을 연결한다. */
@Composable
internal fun RoomHomeRoute(
    viewModel: RoomHomeViewModel,
    roomId: String,
    authSessionState: AuthSessionState,
    sessionIdentity: Any?,
    onBackClick: () -> Unit,
    onEditRoomClick: () -> Unit,
    onManageMembersClick: () -> Unit,
    onMenuActionSucceeded: (RoomHomeMenuActionCompletion) -> Unit,
    onMenuActionHomeClick: (RoomHomeMenuActionCompletion) -> Unit,
    onMenuActionNeedsListRefresh: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onActivityClick: (String, MissionDate) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, roomId, authSessionState, sessionIdentity) {
        viewModel.onRouteVisible(roomId, authSessionState, sessionIdentity)
    }
    LaunchedEffect(viewModel, uiState.menuActionState) {
        when (val actionState = uiState.menuActionState) {
            is RoomHomeMenuActionState.Completed ->
                viewModel.consumeMenuActionCompletion(actionState.operationId)?.let(onMenuActionSucceeded)
            is RoomHomeMenuActionState.Error -> onMenuActionNeedsListRefresh(actionState.operationId)
            else -> Unit
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.startCountdown()
        onPauseOrDispose { viewModel.stopCountdown() }
    }
    RoomHomeScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onEditRoomClick = onEditRoomClick,
        onDeleteRoomClick = viewModel::beginDelete,
        onManageMembersClick = onManageMembersClick,
        onLeaveRoomClick = viewModel::beginLeave,
        onCancelMenuAction = viewModel::cancelMenuAction,
        onConfirmMenuAction = viewModel::confirmMenuAction,
        onRetryMenuAction = viewModel::retryMenuAction,
        onMenuActionHomeClick = { operationId ->
            viewModel.consumeMenuActionHomeNavigation(operationId)?.let(onMenuActionHomeClick)
        },
        modifier = modifier,
        onRetry = onRetry,
        onTabSelected = viewModel::onTabSelected,
        onDateSelected = viewModel::onDateSelected,
        onPreviousWeek = viewModel::onPreviousWeek,
        onNextWeek = viewModel::onNextWeek,
        onOpenCalendar = viewModel::onOpenCalendar,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onDismissCalendar = viewModel::onDismissCalendar,
        onRefresh = viewModel::onRefresh,
        onActivityClick = onActivityClick,
    )
}
