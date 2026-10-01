package com.joon.ringout.presentation.home.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.presentation.home.model.HomeAlarm

@Composable
internal fun HomeAlarmListState(
    alarms: List<HomeAlarm>,
    nextAlarmDescription: String,
    onAddAlarm: () -> Unit,
    onAlarmClick: (String) -> Unit,
    onAlarmEnabledChange: (String, Boolean) -> Unit,
    onAlarmDelete: (String) -> Unit,
    onActiveAlarmMissionClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    activeAlarmMission: ActiveAlarmMission? = null,
    onActiveAlarmMissionExpired: () -> Unit = {},
) {
    val colors = homeAlarmColors()
    val listState = rememberLazyListState()
    val alarmOrder = alarms.map(HomeAlarm::id)
    // 재정렬 전의 화면 위치를 보존한다. 기본 key 추적이 이전 상단 카드를 따라가면
    // 위로 이동한 카드가 화면 밖으로 밀리거나 일부 잘릴 수 있다.
    val previousAlarmOrder = remember { mutableStateOf(alarmOrder) }
    SideEffect {
        if (previousAlarmOrder.value != alarmOrder) {
            listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            previousAlarmOrder.value = alarmOrder
        }
    }
    val activeAlarmMissionRemainingSeconds = if (activeAlarmMission != null) {
        rememberActiveAlarmMissionRemainingSeconds(
            mission = activeAlarmMission,
            onExpired = onActiveAlarmMissionExpired,
        )
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.screenBackground)
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(colors.screenBackground),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                AlarmListHeader(
                    nextAlarmDescription = nextAlarmDescription,
                )

                Spacer(Modifier.height(30.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (
                        activeAlarmMission != null &&
                        activeAlarmMissionRemainingSeconds != null
                    ) {
                        item(key = "active-alarm-mission-${activeAlarmMission.alarmId}") {
                            ActiveAlarmMissionRow(
                                mission = activeAlarmMission,
                                remainingSeconds = activeAlarmMissionRemainingSeconds,
                                onClick = onActiveAlarmMissionClick,
                            )
                        }
                    }

                    items(
                        items = alarms,
                        key = HomeAlarm::id,
                    ) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec = tween(durationMillis = 300),
                                fadeOutSpec = null,
                            ),
                            onClick = { onAlarmClick(alarm.id) },
                            onEnabledChange = { enabled ->
                                onAlarmEnabledChange(alarm.id, enabled)
                            },
                            onDelete = { onAlarmDelete(alarm.id) },
                        )
                    }
                }
            }

            HomeAddAlarmButton(
                onClick = onAddAlarm,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 30.dp, bottom = 114.dp),
            )
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}
