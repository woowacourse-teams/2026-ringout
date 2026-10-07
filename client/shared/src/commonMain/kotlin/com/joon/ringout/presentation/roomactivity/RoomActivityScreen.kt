package com.joon.ringout.presentation.roomactivity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomactivity.component.RoomActivityAllEventsPreviewState
import com.joon.ringout.presentation.roomactivity.component.RoomActivityMemberStrip
import com.joon.ringout.presentation.roomactivity.component.RoomActivityPreviewState
import com.joon.ringout.presentation.roomactivity.component.RoomActivityTimelineRow
import com.joon.ringout.presentation.roomactivity.component.RoomActivityTopBar
import com.joon.ringout.presentation.roomhome.RoomHomeSystemBars
import com.joon.ringout.presentation.roomhome.roomHomeColors

@Composable
internal fun RoomActivityScreen(
    uiState: RoomActivityUiState,
    onBackClick: () -> Unit,
    onMembersClick: (List<String>) -> Unit,
    onRefresh: () -> Unit,
    onRetryMembers: () -> Unit,
    onRetryTimeline: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val isLight = LocalRingoutThemeMode.current == ThemeMode.Light
    RoomHomeSystemBars(useDarkStatusIcons = isLight, useDarkNavigationIcons = isLight)
    Column(
        modifier = modifier.fillMaxSize().background(roomHomeColors().background)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        RoomActivityTopBar("활동", onBackClick, actionLabel = "새로고침", onActionClick = onRefresh)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        ) {
            item(key = "members") {
                RoomActivityMemberStrip(uiState.orderedMembers, onMembersClick)
                Spacer(Modifier.height(20.dp))
            }
            when {
                uiState.isInitialLoading -> item(key = "loading") {
                    RoomActivityStatusMessage("회원 이동 상태를 불러오고 있어요.", isLoading = true)
                }

                uiState.errorMessage != null -> item(key = "error") {
                    RoomActivityStatusMessage(
                        message = uiState.errorMessage,
                        actionLabel = if (uiState.canRetry) "다시 시도" else null,
                        onAction = onRetryMembers,
                    )
                }

                uiState.isEmptySuccess -> item(key = "empty-members") {
                    RoomActivityStatusMessage("표시할 회원 이동 상태가 없어요.")
                }
            }
            if (uiState.refreshErrorMessage != null) {
                item(key = "refresh") {
                    RoomActivityRefreshMessage(
                        message = uiState.refreshErrorMessage,
                        onRetry = onRetryMembers,
                    )
                }
            }
            if (uiState.showTimeline) {
                uiState.timelineDate?.let { date ->
                    item(key = "timeline-date") {
                        Text(
                            text = "${date.iso8601} 활동 기록",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                            color = roomHomeColors().content,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                when {
                    uiState.isTimelineInitialLoading -> item(key = "timeline-loading") {
                        RoomActivityStatusMessage("활동 기록을 불러오고 있어요.", isLoading = true)
                    }

                    uiState.timelineErrorMessage != null -> item(key = "timeline-error") {
                        RoomActivityStatusMessage(
                            message = uiState.timelineErrorMessage,
                            actionLabel = if (uiState.canRetryTimeline) "다시 시도" else null,
                            onAction = onRetryTimeline,
                        )
                    }

                    uiState.isTimelineEmptySuccess -> item(key = "timeline-empty") {
                        RoomActivityStatusMessage("오늘의 활동 기록이 없어요.")
                    }
                }
                if (uiState.timelineRefreshErrorMessage != null) {
                    item(key = "timeline-refresh") {
                        RoomActivityRefreshMessage(
                            message = uiState.timelineRefreshErrorMessage,
                            onRetry = onRetryTimeline,
                        )
                    }
                }
                itemsIndexed(uiState.timeline, key = { _, item -> item.record.id }) { index, item ->
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                        RoomActivityTimelineRow(
                            item = item,
                            onMembersClick = onMembersClick,
                            modifier = Modifier.widthIn(max = 322.dp).fillMaxWidth().padding(bottom = 8.dp),
                            hasPrevious = index > 0,
                            hasNext = index < uiState.timeline.lastIndex && uiState.timeline[index + 1].dateLabel == null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomActivityStatusMessage(
    message: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
        }
        Text(
            message,
            color = roomHomeColors().secondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun RoomActivityRefreshMessage(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        if (message != null) RowActivityRefreshText(message, actionLabel = "다시 시도", onAction = onRetry)
    }
}

@Composable
private fun RowActivityRefreshText(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            message,
            color = roomHomeColors().secondary,
            style = MaterialTheme.typography.bodySmall,
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Preview(name = "모임 활동 · 도착과 포기", widthDp = 402, heightDp = 941)
@Composable
private fun RoomActivityScreenPreview() {
    RingoutTheme(ThemeMode.Dark) { RoomActivityScreen(RoomActivityPreviewState, {}, {}, {}, {}, {}) }
}

@Preview(name = "모임 활동 · 모든 이벤트 · 라이트", widthDp = 402, heightDp = 941)
@Composable
private fun RoomActivityScreenLightPreview() {
    RingoutTheme(ThemeMode.Light) { RoomActivityScreen(RoomActivityAllEventsPreviewState, {}, {}, {}, {}, {}) }
}

@Preview(name = "모임 활동 · 기록 없음", widthDp = 402, heightDp = 941)
@Composable
private fun RoomActivityScreenEmptyPreview() {
    RingoutTheme {
        RoomActivityScreen(
            RoomActivityPreviewState.copy(timeline = emptyList()),
            {}, {}, {}, {}, {},
        )
    }
}

@Preview(name = "모임 활동 · 작은 화면과 큰 글씨", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun RoomActivityScreenCompactPreview() {
    RingoutTheme { RoomActivityScreen(RoomActivityAllEventsPreviewState, {}, {}, {}, {}, {}) }
}
