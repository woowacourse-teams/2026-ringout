package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.roomhome.RoomHomeRecordEvent
import com.joon.ringout.presentation.roomhome.RoomHomeRecordsUiState
import com.joon.ringout.presentation.roomhome.roomHomeColors

@Composable
internal fun RoomHomeRecords(
    uiState: RoomHomeRecordsUiState,
    onDateSelected: (MissionDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onOpenCalendar: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = roomHomeColors()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!uiState.canViewRecords) {
            RoomHomeStatus(
                message = "모임에 가입한 회원만 기록을 볼 수 있어요.",
                modifier = Modifier.heightIn(min = 180.dp),
            )
            return@Column
        }

        RoomHomeWeekCalendar(
            selectedDate = uiState.selectedDate,
            visibleWeekStart = uiState.visibleWeekStart,
            participantCounts = uiState.participantCounts,
            onDateSelected = onDateSelected,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onOpenCalendar = onOpenCalendar,
        )

        when {
            uiState.isLoading -> RoomHomeStatus(
                message = "기록을 불러오는 중이에요.",
                modifier = Modifier.heightIn(min = 180.dp),
                isLoading = true,
            )
            uiState.errorMessage != null -> RoomHomeStatus(
                message = uiState.errorMessage,
                modifier = Modifier.heightIn(min = 180.dp),
                onRetry = onRefresh,
            )
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "목표 달성 사용자",
                        modifier = Modifier.weight(1f).semantics { heading() },
                        color = colors.content,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    TextButton(onClick = onRefresh) {
                        Text("새로고침", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        text = "${uiState.achievedMemberCount.coerceAtLeast(0)}명",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                if (uiState.records.isEmpty()) {
                    RoomHomeStatus(
                        message = "이 날짜에는 알람 사용 기록이 없어요.",
                        modifier = Modifier.heightIn(min = 140.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        uiState.records.forEachIndexed { index, record ->
                            RoomHomeRecordRow(
                                record = record,
                                hasPrevious = index > 0,
                                hasNext = index < uiState.records.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "모임 기록 · 라이트", widthDp = 402)
@Composable
private fun RoomHomeRecordsPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeRecords(
            uiState = RoomHomePreviewState.recordsState,
            onDateSelected = {}, onPreviousWeek = {}, onNextWeek = {}, onOpenCalendar = {}, onRefresh = {},
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 30.dp),
        )
    }
}

@Preview(name = "모임 기록 · 다크", widthDp = 402)
@Composable
private fun RoomHomeRecordsDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeRecords(
            uiState = RoomHomePreviewState.recordsState.copy(
                records = RoomHomePreviewRecords.filter { it.event != RoomHomeRecordEvent.Ringing && it.event != RoomHomeRecordEvent.Dismissed },
            ),
            onDateSelected = {}, onPreviousWeek = {}, onNextWeek = {}, onOpenCalendar = {}, onRefresh = {},
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 30.dp),
        )
    }
}

@Preview(name = "모임 기록 · 빈 날짜", widthDp = 402)
@Composable
private fun RoomHomeRecordsEmptyPreview() {
    RingoutTheme {
        RoomHomeRecords(
            uiState = RoomHomeRecordsUiState(selectedDate = RoomHomePreviewDate),
            onDateSelected = {}, onPreviousWeek = {}, onNextWeek = {}, onOpenCalendar = {}, onRefresh = {},
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 30.dp),
        )
    }
}

@Preview(name = "모임 기록 · 로딩", widthDp = 402)
@Composable
private fun RoomHomeRecordsLoadingPreview() {
    RingoutTheme {
        RoomHomeRecords(
            uiState = RoomHomePreviewState.recordsState.copy(isLoading = true),
            onDateSelected = {}, onPreviousWeek = {}, onNextWeek = {}, onOpenCalendar = {}, onRefresh = {},
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 30.dp),
        )
    }
}

@Preview(name = "모임 기록 · 오류", widthDp = 402)
@Composable
private fun RoomHomeRecordsErrorPreview() {
    RingoutTheme {
        RoomHomeRecords(
            uiState = RoomHomePreviewState.recordsState.copy(errorMessage = "기록을 불러오지 못했어요."),
            onDateSelected = {}, onPreviousWeek = {}, onNextWeek = {}, onOpenCalendar = {}, onRefresh = {},
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 30.dp),
        )
    }
}

@Preview(name = "모임 기록 · 가입 필요", widthDp = 402)
@Composable
private fun RoomHomeRecordsRestrictedPreview() {
    RingoutTheme {
        RoomHomeRecords(
            uiState = RoomHomePreviewState.recordsState.copy(canViewRecords = false),
            onDateSelected = {}, onPreviousWeek = {}, onNextWeek = {}, onOpenCalendar = {}, onRefresh = {},
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 30.dp),
        )
    }
}
