package com.joon.ringout.presentation.roomcreate.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.alarmsetup.AlarmTimePickerValue
import com.joon.ringout.presentation.alarmsetup.components.TimePickerCard
import com.joon.ringout.presentation.alarmsetup.components.WeekdaySelector
import com.joon.ringout.presentation.alarmsetup.toAlarmTimePickerValue
import com.joon.ringout.presentation.common.WeekdayOrder

@Composable
internal fun RoomCreateScheduleSection(
    selectedDays: List<String>,
    time: AlarmTimePickerValue,
    onDayClick: (String) -> Unit,
    onAmPmChange: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "모임의 활동 시간을\n알려주세요",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        WeekdaySelector(
            selectedDays = selectedDays,
            onDayClick = onDayClick,
        )
        if (selectedDays.isEmpty()) {
            Text(
                text = "요일을 하나 이상 선택해주세요",
                color = roomCreateColors().error,
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TimePickerCard(
            isAm = time.isAm,
            hour = time.hour,
            minute = time.minute,
            onAmPmChange = onAmPmChange,
            onHourChange = onHourChange,
            onMinuteChange = onMinuteChange,
        )
    }
}

@Preview(name = "요일 선택 없음")
@Composable
private fun RoomCreateScheduleSectionNoDaysPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomCreateScheduleSection(
            selectedDays = emptyList(),
            time = "12:05".toAlarmTimePickerValue(),
            onDayClick = {},
            onAmPmChange = {},
            onHourChange = {},
            onMinuteChange = {},
        )
    }
}

@Preview(name = "매일 활동 시간")
@Composable
private fun RoomCreateScheduleSectionAllDaysPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomCreateScheduleSection(
            selectedDays = WeekdayOrder,
            time = "06:20".toAlarmTimePickerValue(),
            onDayClick = {},
            onAmPmChange = {},
            onHourChange = {},
            onMinuteChange = {},
        )
    }
}
