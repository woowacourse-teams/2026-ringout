package com.joon.ringout.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.presentation.home.components.HomeAlarmListState
import com.joon.ringout.presentation.home.components.HomeEmptyState
import com.joon.ringout.presentation.home.components.HomeLoadingState
import com.joon.ringout.presentation.home.model.HomeAlarm
import com.joon.ringout.presentation.home.model.HomeUiState

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAddAlarm: () -> Unit,
    onAlarmClick: (String) -> Unit,
    onAlarmEnabledChange: (String, Boolean) -> Unit,
    onAlarmDelete: (String) -> Unit,
    onMyPageClick: () -> Unit,
    onActiveAlarmMissionClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    activeAlarmMission: ActiveAlarmMission? = null,
    onActiveAlarmMissionExpired: () -> Unit = {},
) {
    val alarms = uiState.alarms
    if (uiState.isLoading && activeAlarmMission == null) {
        HomeLoadingState(modifier = modifier)
        return
    }

    if (alarms.isEmpty() && activeAlarmMission == null) {
        HomeEmptyState(
            onAddAlarm = onAddAlarm,
            onMyPageClick = onMyPageClick,
            modifier = modifier,
        )
        return
    }

    val orderedAlarms = remember(alarms) {
        enabledAlarmsFirst(alarms)
    }
    HomeAlarmListState(
        alarms = orderedAlarms,
        nextAlarmDescription = rememberNextAlarmDescription(alarms),
        onAddAlarm = onAddAlarm,
        onAlarmClick = onAlarmClick,
        onAlarmEnabledChange = onAlarmEnabledChange,
        onAlarmDelete = onAlarmDelete,
        onMyPageClick = onMyPageClick,
        onActiveAlarmMissionClick = onActiveAlarmMissionClick,
        modifier = modifier,
        activeAlarmMission = activeAlarmMission,
        onActiveAlarmMissionExpired = onActiveAlarmMissionExpired,
    )
}

internal fun enabledAlarmsFirst(alarms: List<HomeAlarm>): List<HomeAlarm> {
    val (enabledAlarms, disabledAlarms) = alarms.partition(HomeAlarm::isEnabled)
    return enabledAlarms + disabledAlarms
}

@Preview(name = "Dark loading Home", widthDp = 402, heightDp = 941)
@Composable
private fun DarkLoadingHomeScreenPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        HomeScreen(
            uiState = HomeUiState(),
            onAddAlarm = {},
            onAlarmClick = {},
            onAlarmEnabledChange = { _, _ -> },
            onAlarmDelete = {},
            onMyPageClick = {},
        )
    }
}

@Preview(name = "Dark empty Home", widthDp = 402, heightDp = 941)
@Composable
private fun DarkEmptyHomeScreenPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        HomeScreen(
            uiState = HomeUiState(isLoading = false),
            onAddAlarm = {},
            onAlarmClick = {},
            onAlarmEnabledChange = { _, _ -> },
            onAlarmDelete = {},
            onMyPageClick = {},
        )
    }
}

@Preview(name = "Light empty Home", widthDp = 402, heightDp = 941)
@Composable
private fun LightEmptyHomeScreenPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        HomeScreen(
            uiState = HomeUiState(isLoading = false),
            onAddAlarm = {},
            onAlarmClick = {},
            onAlarmEnabledChange = { _, _ -> },
            onAlarmDelete = {},
            onMyPageClick = {},
        )
    }
}

@Preview(name = "Dark populated Home", widthDp = 402, heightDp = 941)
@Composable
private fun DarkPopulatedHomeScreenPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        HomeAlarmListState(
            alarms = previewAlarms(),
            nextAlarmDescription = "7시간 20분 후 알람이 울려요.",
            onAddAlarm = {},
            onAlarmClick = {},
            onAlarmEnabledChange = { _, _ -> },
            onAlarmDelete = {},
            onMyPageClick = {},
        )
    }
}

@Preview(name = "Light populated Home", widthDp = 402, heightDp = 941)
@Composable
private fun LightPopulatedHomeScreenPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        HomeAlarmListState(
            alarms = previewAlarms(),
            nextAlarmDescription = "7시간 20분 후 알람이 울려요.",
            onAddAlarm = {},
            onAlarmClick = {},
            onAlarmEnabledChange = { _, _ -> },
            onAlarmDelete = {},
            onMyPageClick = {},
        )
    }
}

private fun previewAlarms() = listOf(
    HomeAlarm(
        id = "weekend",
        time = "06:20",
        days = "주말",
        destination = "헬스장",
        timeLimitMinutes = 12,
        isEnabled = true,
        selectedDays = listOf("토", "일"),
    ),
    HomeAlarm(
        id = "weekday",
        time = "06:20",
        days = "월 화 수 목",
        destination = "헬스장",
        timeLimitMinutes = 12,
        isEnabled = false,
        selectedDays = listOf("월", "화", "수", "목"),
    ),
)
