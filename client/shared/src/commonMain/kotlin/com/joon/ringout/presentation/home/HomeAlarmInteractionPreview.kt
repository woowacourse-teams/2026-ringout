package com.joon.ringout.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.home.components.HomeAlarmListState
import com.joon.ringout.presentation.home.model.HomeAlarm

/** 실제 알람을 예약하지 않고 ON/OFF 재배치와 버튼 터치 효과를 확인한다. */
@Preview(name = "알람 ON/OFF · 다크", widthDp = 402, heightDp = 941)
@Composable
private fun HomeAlarmInteractionPreview() {
    HomeAlarmInteractionContent(ThemeMode.Dark)
}

@Preview(name = "알람 ON/OFF · 라이트", widthDp = 402, heightDp = 941)
@Composable
private fun HomeAlarmInteractionLightPreview() {
    HomeAlarmInteractionContent(ThemeMode.Light)
}

@Composable
private fun HomeAlarmInteractionContent(themeMode: ThemeMode) {
    var alarms by remember {
        mutableStateOf(
            listOf(
                HomeAlarm("morning", "06:00", "매일", "아침 산책", 30, false),
                HomeAlarm("work", "08:00", "평일", "회사", 30, true),
                HomeAlarm("exercise", "19:00", "매일", "헬스장", 30, false),
            ),
        )
    }
    RingoutTheme(themeMode) {
        HomeAlarmListState(
            alarms = enabledAlarmsFirst(alarms),
            nextAlarmDescription = "알람 버튼을 눌러 순서 변경을 확인해 보세요.",
            onAddAlarm = {},
            onAlarmClick = {},
            onAlarmEnabledChange = { id, enabled ->
                alarms = alarms.map { if (it.id == id) it.copy(isEnabled = enabled) else it }
            },
            onAlarmDelete = {},
        )
    }
}
