package com.joon.ringout.presentation.roomhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joon.ringout.domain.missionhistory.MissionDate

/** API 없이 정보·기록 탭과 날짜 선택 상태를 연결한다. 메뉴의 후속 동작은 호출부에서 연결한다. */
@Composable
internal fun RoomHomeRoute(
    viewModel: RoomHomeViewModel,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onActivityClick: (String, MissionDate) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.startCountdown()
        onPauseOrDispose { viewModel.stopCountdown() }
    }
    RoomHomeScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onMenuClick = onMenuClick,
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
