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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val isLight = LocalRingoutThemeMode.current == ThemeMode.Light
    RoomHomeSystemBars(useDarkStatusIcons = isLight, useDarkNavigationIcons = isLight)
    Column(
        modifier = modifier.fillMaxSize().background(roomHomeColors().background)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        RoomActivityTopBar("활동", onBackClick)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        ) {
            item(key = "members") {
                RoomActivityMemberStrip(uiState.orderedMembers, onMembersClick)
                Spacer(Modifier.height(20.dp))
            }
            if (uiState.timeline.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "아직 활동 기록이 없어요.",
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 20.dp),
                        color = roomHomeColors().secondary,
                        style = MaterialTheme.typography.bodyMedium,
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

@Preview(name = "모임 활동 · 도착과 포기", widthDp = 402, heightDp = 941)
@Composable
private fun RoomActivityScreenPreview() {
    RingoutTheme(ThemeMode.Dark) { RoomActivityScreen(RoomActivityPreviewState, {}, {}) }
}

@Preview(name = "모임 활동 · 모든 이벤트 · 라이트", widthDp = 402, heightDp = 941)
@Composable
private fun RoomActivityScreenLightPreview() {
    RingoutTheme(ThemeMode.Light) { RoomActivityScreen(RoomActivityAllEventsPreviewState, {}, {}) }
}

@Preview(name = "모임 활동 · 기록 없음", widthDp = 402, heightDp = 941)
@Composable
private fun RoomActivityScreenEmptyPreview() {
    RingoutTheme { RoomActivityScreen(RoomActivityPreviewState.copy(timeline = emptyList()), {}, {}) }
}

@Preview(name = "모임 활동 · 작은 화면과 큰 글씨", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun RoomActivityScreenCompactPreview() {
    RingoutTheme { RoomActivityScreen(RoomActivityAllEventsPreviewState, {}, {}) }
}
