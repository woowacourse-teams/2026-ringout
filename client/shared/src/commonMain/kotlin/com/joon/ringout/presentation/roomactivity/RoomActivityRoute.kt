package com.joon.ringout.presentation.roomactivity

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
internal fun RoomActivityRoute(
    viewModel: RoomActivityViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        RoomActivityScreen(state, onBackClick, viewModel::onMembersClick, modifier, timelineState)
    }
}
