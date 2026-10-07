package com.joon.ringout.presentation.roomactivity

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.joon.ringout.domain.auth.AuthSessionState

@Composable
internal fun RoomActivityRoute(
    viewModel: RoomActivityViewModel,
    roomId: String,
    activityDate: String,
    authSessionState: AuthSessionState,
    sessionIdentity: Any?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, roomId, activityDate, authSessionState, sessionIdentity) {
        viewModel.onRouteVisible(roomId, activityDate, authSessionState, sessionIdentity)
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.onPause() }
    }
    // 회원 목록을 열었다 닫아도 읽던 타임라인 위치를 유지한다.
    val timelineState = rememberLazyListState()
    val showingMembers = state.selectedMemberIds != null
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = showingMembers,
        onBackCompleted = viewModel::onCloseMembers,
    )
    if (showingMembers) {
        RoomActivityMembersScreen(state.selectedMembers, viewModel::onCloseMembers, modifier)
    } else {
        RoomActivityScreen(
            uiState = state,
            onBackClick = onBackClick,
            onMembersClick = viewModel::onMembersClick,
            onRefresh = viewModel::onRefresh,
            onRetryMembers = viewModel::onRetryMembers,
            onRetryTimeline = viewModel::onRetryTimeline,
            modifier = modifier,
            listState = timelineState,
        )
    }
}
