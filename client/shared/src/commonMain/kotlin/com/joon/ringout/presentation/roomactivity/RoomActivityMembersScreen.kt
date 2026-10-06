package com.joon.ringout.presentation.roomactivity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomactivity.component.RoomActivityMemberRow
import com.joon.ringout.presentation.roomactivity.component.RoomActivityPreviewState
import com.joon.ringout.presentation.roomactivity.component.RoomActivityTopBar
import com.joon.ringout.presentation.roomhome.RoomHomeSystemBars
import com.joon.ringout.presentation.roomhome.roomHomeColors

/** 활동 화면과 ViewModel을 공유하며 선택된 회원을 표시하는 하위 화면. */
@Composable
internal fun RoomActivityMembersScreen(
    members: List<RoomActivityMemberUiModel>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = LocalRingoutThemeMode.current == ThemeMode.Light
    RoomHomeSystemBars(useDarkStatusIcons = isLight, useDarkNavigationIcons = isLight)
    Column(
        modifier = modifier.fillMaxSize().background(roomHomeColors().background)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        RoomActivityTopBar("활동중인 회원", onBackClick)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(members, key = { it.id }) { member -> RoomActivityMemberRow(member) }
            if (members.isEmpty()) {
                item {
                    Text(
                        "표시할 회원이 없어요.",
                        modifier = Modifier.padding(20.dp),
                        color = roomHomeColors().secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Preview(name = "활동중인 회원 · 다크", widthDp = 402, heightDp = 941)
@Composable
private fun RoomActivityMembersScreenPreview() {
    RingoutTheme(ThemeMode.Dark) { RoomActivityMembersScreen(RoomActivityPreviewState.orderedMembers, {}) }
}

@Preview(name = "활동중인 회원 · 라이트", widthDp = 402, heightDp = 941)
@Composable
private fun RoomActivityMembersScreenLightPreview() {
    RingoutTheme(ThemeMode.Light) { RoomActivityMembersScreen(RoomActivityPreviewState.orderedMembers, {}) }
}
