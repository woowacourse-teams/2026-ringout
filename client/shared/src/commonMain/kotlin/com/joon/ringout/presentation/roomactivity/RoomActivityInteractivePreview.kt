package com.joon.ringout.presentation.roomactivity

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomactivity.component.RoomActivityAllEventsPreviewState
import com.joon.ringout.presentation.roomactivity.component.RoomActivityPreviewState
import com.joon.ringout.presentation.roomhome.RoomHomeOngoingActivityUiModel
import com.joon.ringout.presentation.roomhome.RoomHomeScreen
import com.joon.ringout.presentation.roomhome.component.RoomHomePreviewState

/** 모임 홈 → 활동 → 회원 목록의 UI만 확인하는 Preview. 서비스·ViewModel에 의존하지 않는다. */
@Preview(name = "모임 활동 · 홈부터 동작 확인", widthDp = 402, heightDp = 941)
@Composable
private fun RoomActivityInteractivePreview() {
    var showingActivity by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(RoomActivityAllEventsPreviewState) }
    val timelineState = rememberLazyListState()
    val stateHolder = rememberSaveableStateHolder()
    RingoutTheme(ThemeMode.Dark) {
        if (!showingActivity) {
            stateHolder.SaveableStateProvider("home") {
                RoomHomeScreen(
                    uiState = RoomHomePreviewState.copy(
                        ongoingActivity = RoomHomeOngoingActivityUiModel(
                            checkNotNull(state.activityDate),
                            participantCount = 3,
                        ),
                    ),
                    onBackClick = {},
                    onActivityClick = { roomId, date ->
                        state = state.copy(roomId = roomId, activityDate = date)
                        showingActivity = true
                    },
                )
            }
        } else if (state.selectedMemberIds != null) {
            RoomActivityMembersScreen(state.selectedMembers, { state = state.copy(selectedMemberIds = null) })
        } else {
            RoomActivityScreen(
                state,
                onBackClick = { showingActivity = false },
                onMembersClick = { state = state.copy(selectedMemberIds = it) },
                onRefresh = {},
                onRetryMembers = {},
                onRetryTimeline = {},
                listState = timelineState,
            )
        }
    }
}

@Preview(name = "모임 활동 · 회원 더보기 동작 확인", widthDp = 402, heightDp = 941)
@Composable
private fun RoomActivityMembersInteractivePreview() {
    var selected by remember { mutableStateOf<List<String>?>(null) }
    val listState = rememberLazyListState()
    RingoutTheme(ThemeMode.Dark) {
        if (selected == null) {
            RoomActivityScreen(RoomActivityPreviewState, {}, { selected = it }, {}, {}, {}, listState = listState)
        } else {
            RoomActivityMembersScreen(
                RoomActivityPreviewState.members.filter { it.id in selected.orEmpty() },
                { selected = null },
            )
        }
    }
}
