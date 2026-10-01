package com.joon.ringout.presentation.roomhome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.calendarDates
import com.joon.ringout.presentation.records.component.RecordsMonthDialog
import com.joon.ringout.domain.room.roomRecordsDate
import com.joon.ringout.presentation.records.recordsDayUiState
import com.joon.ringout.presentation.roomhome.component.RoomHomeDescription
import com.joon.ringout.presentation.roomhome.component.RoomHomeHeader
import com.joon.ringout.presentation.roomhome.component.RoomHomeHero
import com.joon.ringout.presentation.roomhome.component.RoomHomeMembers
import com.joon.ringout.presentation.roomhome.component.RoomHomePreviewState
import com.joon.ringout.presentation.roomhome.component.RoomHomeRecords
import com.joon.ringout.presentation.roomhome.component.RoomHomeSchedule
import com.joon.ringout.presentation.roomhome.component.RoomHomeStatus
import com.joon.ringout.presentation.roomhome.component.RoomHomeTabs

/** 모임 홈의 정보·기록 탭. 네트워크 조회와 일정 계산은 화면에서 수행하지 않는다. */
@Composable
internal fun RoomHomeScreen(
    uiState: RoomHomeUiState,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onTabSelected: (RoomHomeTab) -> Unit = {},
    onDateSelected: (MissionDate) -> Unit = {},
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onDismissCalendar: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onActivityClick: (String, MissionDate) -> Unit = { _, _ -> },
) {
    val room = uiState.room
    val listState = rememberLazyListState()
    val isHeroVisible by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val isLight = LocalRingoutThemeMode.current == ThemeMode.Light
    RoomHomeSystemBars(
        useDarkStatusIcons = isLight && (!isHeroVisible || room == null || uiState.isLoading || uiState.errorMessage != null),
        useDarkNavigationIcons = isLight,
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(roomHomeColors().background)
            .navigationBarsPadding(),
    ) {
        if (uiState.isLoading || uiState.errorMessage != null || room == null) {
            TextButton(
                onClick = onBackClick,
                modifier = Modifier.statusBarsPadding().padding(horizontal = 12.dp),
            ) { Text("뒤로") }
            RoomHomeStatus(
                message = when {
                    uiState.isLoading -> "모임 정보를 불러오는 중이에요."
                    uiState.errorMessage != null -> uiState.errorMessage
                    else -> "표시할 모임 정보가 없어요."
                },
                modifier = Modifier.weight(1f),
                isLoading = uiState.isLoading,
                onRetry = onRetry.takeIf {
                    !uiState.isLoading && uiState.errorMessage != null && uiState.canRetry
                },
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item(key = "hero") {
                    RoomHomeHero(
                        room = room,
                        onBackClick = onBackClick,
                        onMenuClick = onMenuClick,
                    )
                }
                item(key = "header") {
                    RoomHomeHeader(
                        room = room,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp),
                    )
                }
                item(key = "tabs") {
                    RoomHomeTabs(
                        selectedTab = uiState.selectedTab,
                        onTabSelected = onTabSelected,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp),
                    )
                }
                if (uiState.selectedTab == RoomHomeTab.Info) {
                    item(key = "schedule") {
                        RoomHomeSchedule(
                            activityDaysText = room.activityDaysText,
                            activityTimeText = room.activityTimeText,
                            nextScheduleText = uiState.nextScheduleText,
                            remainingTimeText = uiState.remainingTimeText,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp),
                            ongoingParticipantCount = uiState.ongoingActivity?.participantCount,
                            onActivityClick = {
                                uiState.ongoingActivity?.let { onActivityClick(room.id, it.date) }
                            },
                        )
                    }
                    item(key = "members") {
                        RoomHomeMembers(
                            members = uiState.members,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp),
                            isLoaded = uiState.areMembersLoaded,
                        )
                    }
                    item(key = "description") {
                        RoomHomeDescription(
                            description = room.description,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 20.dp),
                        )
                    }
                } else {
                    item(key = "records") {
                        RoomHomeRecords(
                            uiState = uiState.recordsState.copy(
                                canViewRecords = room.isJoined && uiState.recordsState.canViewRecords,
                            ),
                            onDateSelected = onDateSelected,
                            onPreviousWeek = onPreviousWeek,
                            onNextWeek = onNextWeek,
                            onOpenCalendar = onOpenCalendar,
                            onRefresh = onRefresh,
                            modifier = Modifier.padding(horizontal = 30.dp).padding(top = 10.dp, bottom = 20.dp),
                        )
                    }
                }
            }
        }
    }

    if (uiState.isCalendarVisible && uiState.selectedTab == RoomHomeTab.Records &&
        room?.isJoined == true && uiState.recordsState.canViewRecords
    ) {
        val today = roomRecordsDate()
        RecordsMonthDialog(
            month = uiState.calendarMonth,
            days = uiState.calendarMonth.calendarDates().map { date ->
                date?.let { recordsDayUiState(it, today, uiState.recordsState.selectedDate) }
            },
            isLoading = false,
            errorMessage = null,
            onDateSelected = onDateSelected,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onDismiss = onDismissCalendar,
            onRetry = onRefresh,
        )
    }
}

@Preview(name = "모임 홈 · 다크", widthDp = 402, heightDp = 949)
@Composable
private fun RoomHomeScreenDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeScreen(RoomHomePreviewState, onBackClick = {}, onMenuClick = {})
    }
}

@Preview(name = "모임 홈 · 라이트", widthDp = 402, heightDp = 949)
@Composable
private fun RoomHomeScreenLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeScreen(RoomHomePreviewState, onBackClick = {}, onMenuClick = {})
    }
}

@Preview(name = "모임 홈 · 하루 이상 남은 일정", widthDp = 402, heightDp = 949)
@Composable
private fun RoomHomeScreenLongCountdownPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeScreen(
            RoomHomePreviewState.copy(nextScheduleText = "내일 오후 06:00", remainingTimeText = "1일 12시간"),
            onBackClick = {},
            onMenuClick = {},
        )
    }
}

@Preview(name = "모임 홈 · 작은 화면과 큰 글씨", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun RoomHomeScreenCompactPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeScreen(
            RoomHomePreviewState.copy(
                room = RoomHomePreviewState.room?.copy(name = "아침마다 함께 달리며 하루를 시작하는 사람들"),
                members = RoomHomePreviewState.members.map {
                    it.copy(nickname = "아주긴닉네임으로모임에참여한회원입니다")
                },
            ),
            onBackClick = {},
            onMenuClick = {},
        )
    }
}

@Preview(name = "모임 홈 · 회원과 일정 없음", widthDp = 402, heightDp = 949)
@Composable
private fun RoomHomeScreenEmptyPreview() {
    RingoutTheme {
        RoomHomeScreen(
            RoomHomePreviewState.copy(members = emptyList(), nextScheduleText = null, remainingTimeText = null),
            onBackClick = {},
            onMenuClick = {},
        )
    }
}

@Preview(name = "모임 홈 · 로딩", widthDp = 402, heightDp = 949)
@Composable
private fun RoomHomeScreenLoadingPreview() {
    RingoutTheme {
        RoomHomeScreen(RoomHomeUiState(isLoading = true), onBackClick = {}, onMenuClick = {})
    }
}

@Preview(name = "모임 홈 · 오류", widthDp = 402, heightDp = 949)
@Composable
private fun RoomHomeScreenErrorPreview() {
    RingoutTheme {
        RoomHomeScreen(
            RoomHomeUiState(errorMessage = "모임 정보를 불러오지 못했어요.", canRetry = true),
            onBackClick = {},
            onMenuClick = {},
            onRetry = {},
        )
    }
}
