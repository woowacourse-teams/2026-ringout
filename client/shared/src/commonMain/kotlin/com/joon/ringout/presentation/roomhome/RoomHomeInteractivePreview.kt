package com.joon.ringout.presentation.roomhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.plusDays
import com.joon.ringout.domain.missionhistory.weekDates
import com.joon.ringout.domain.missionhistory.yearMonth
import com.joon.ringout.presentation.roomhome.component.RoomHomePreviewRecordsByDate
import com.joon.ringout.presentation.roomhome.component.RoomHomePreviewState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 실행 모드에서 탭·주간 날짜·월간 달력·새로고침을 확인하는 독립 Preview. */
@Preview(name = "모임 홈 · 동작 확인", widthDp = 402, heightDp = 949)
@Composable
private fun RoomHomeInteractivePreview() {
    var state by remember { mutableStateOf(RoomHomePreviewState) }
    val clock = remember { systemRoomScheduleClock() }
    LaunchedEffect(state.room) {
        while (isActive) {
            state = state.withCurrentSchedule(clock)
            delay(1_000)
        }
    }
    val selectDate: (MissionDate) -> Unit = { date ->
        val day = RoomHomePreviewRecordsByDate[date] ?: RoomHomeDayRecordsUiModel()
        state = state.copy(
            recordsState = state.recordsState.copy(
                selectedDate = date,
                visibleWeekStart = date.weekDates().first(),
                records = day.records,
                achievedMemberCount = day.achievedMemberCount,
            ),
            calendarMonth = date.yearMonth,
            isCalendarVisible = false,
        )
    }
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeScreen(
            uiState = state,
            onBackClick = {},
            onMenuClick = {},
            onTabSelected = { state = state.copy(selectedTab = it, isCalendarVisible = false) },
            onDateSelected = selectDate,
            onPreviousWeek = { selectDate(state.recordsState.selectedDate.plusDays(-7)) },
            onNextWeek = { selectDate(state.recordsState.selectedDate.plusDays(7)) },
            onOpenCalendar = {
                state = state.copy(isCalendarVisible = true, calendarMonth = state.recordsState.selectedDate.yearMonth)
            },
            onPreviousMonth = { state = state.copy(calendarMonth = state.calendarMonth.previous()) },
            onNextMonth = { state = state.copy(calendarMonth = state.calendarMonth.next()) },
            onDismissCalendar = { state = state.copy(isCalendarVisible = false) },
            onRefresh = { selectDate(state.recordsState.selectedDate) },
        )
    }
}

@Preview(name = "모임 기록 · 다크", widthDp = 402, heightDp = 1041)
@Composable
private fun RoomHomeRecordsScreenPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeScreen(RoomHomePreviewState.copy(selectedTab = RoomHomeTab.Records), onBackClick = {}, onMenuClick = {})
    }
}

@Preview(name = "모임 기록 · 라이트", widthDp = 402, heightDp = 1041)
@Composable
private fun RoomHomeRecordsScreenLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeScreen(RoomHomePreviewState.copy(selectedTab = RoomHomeTab.Records), onBackClick = {}, onMenuClick = {})
    }
}

@Preview(name = "모임 기록 · 빈 날짜", widthDp = 402, heightDp = 949)
@Composable
private fun RoomHomeRecordsScreenEmptyPreview() {
    RingoutTheme {
        RoomHomeScreen(
            RoomHomePreviewState.copy(
                selectedTab = RoomHomeTab.Records,
                recordsState = RoomHomePreviewState.recordsState.copy(records = emptyList(), achievedMemberCount = 0),
            ),
            onBackClick = {}, onMenuClick = {},
        )
    }
}

@Preview(name = "모임 기록 · 로딩", widthDp = 402, heightDp = 949)
@Composable
private fun RoomHomeRecordsScreenLoadingPreview() {
    RingoutTheme {
        RoomHomeScreen(
            RoomHomePreviewState.copy(
                selectedTab = RoomHomeTab.Records,
                recordsState = RoomHomePreviewState.recordsState.copy(isLoading = true),
            ),
            onBackClick = {}, onMenuClick = {},
        )
    }
}

@Preview(name = "모임 기록 · 오류", widthDp = 402, heightDp = 949)
@Composable
private fun RoomHomeRecordsScreenErrorPreview() {
    RingoutTheme {
        RoomHomeScreen(
            RoomHomePreviewState.copy(
                selectedTab = RoomHomeTab.Records,
                recordsState = RoomHomePreviewState.recordsState.copy(errorMessage = "기록을 불러오지 못했어요."),
            ),
            onBackClick = {}, onMenuClick = {},
        )
    }
}

@Preview(name = "모임 기록 · 접근 제한", widthDp = 402, heightDp = 949)
@Composable
private fun RoomHomeRecordsScreenNoAccessPreview() {
    RingoutTheme {
        RoomHomeScreen(
            RoomHomePreviewState.copy(
                selectedTab = RoomHomeTab.Records,
                room = RoomHomePreviewState.room?.copy(isJoined = false),
            ),
            onBackClick = {}, onMenuClick = {},
        )
    }
}

@Preview(name = "모임 기록 · 달력", widthDp = 402, heightDp = 1041)
@Composable
private fun RoomHomeRecordsScreenCalendarPreview() {
    RingoutTheme {
        RoomHomeScreen(
            RoomHomePreviewState.copy(selectedTab = RoomHomeTab.Records, isCalendarVisible = true),
            onBackClick = {}, onMenuClick = {},
        )
    }
}
