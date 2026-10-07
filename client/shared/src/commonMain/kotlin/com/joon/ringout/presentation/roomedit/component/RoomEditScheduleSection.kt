package com.joon.ringout.presentation.roomedit.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.alarmsetup.AlarmTimePickerValue
import com.joon.ringout.presentation.alarmsetup.components.TimePickerCard
import com.joon.ringout.presentation.alarmsetup.components.WeekdaySelector
import com.joon.ringout.presentation.alarmsetup.toAlarmTimePickerValue
import com.joon.ringout.presentation.common.WeekdayOrder

@Composable
internal fun RoomEditScheduleSection(
    selectedDays: List<String>,
    time: AlarmTimePickerValue,
    onDayClick: (String) -> Unit,
    onAmPmChange: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TimePickerCard(
            isAm = time.isAm,
            hour = time.hour,
            minute = time.minute,
            onAmPmChange = onAmPmChange,
            onHourChange = onHourChange,
            onMinuteChange = onMinuteChange,
        )
        WeekdaySelector(selectedDays = selectedDays, onDayClick = onDayClick)
        if (selectedDays.isEmpty()) {
            Text(
                text = "요일을 하나 이상 선택해주세요",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(name = "활동 일정 · 매일 · 다크", widthDp = 354)
@Composable
private fun RoomEditScheduleSectionPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomEditScheduleSection(WeekdayOrder, "06:20".toAlarmTimePickerValue(), {}, {}, {}, {})
    }
}

@Preview(name = "활동 일정 · 미선택 · 라이트", widthDp = 272)
@Composable
private fun RoomEditScheduleSectionEmptyPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomEditScheduleSection(emptyList(), "12:00".toAlarmTimePickerValue(), {}, {}, {}, {})
    }
}
