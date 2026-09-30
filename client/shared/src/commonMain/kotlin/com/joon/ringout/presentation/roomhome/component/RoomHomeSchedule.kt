package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomhome.roomHomeColors
import com.joon.ringout.presentation.roomlist.roomdetail.component.RoomActivitySummary
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.social_room_activity_days
import ringout.shared.generated.resources.social_room_activity_time

@Composable
internal fun RoomHomeSchedule(
    activityDaysText: String,
    activityTimeText: String,
    nextScheduleText: String?,
    remainingTimeText: String?,
    modifier: Modifier = Modifier,
) {
    val colors = roomHomeColors()
    val countdownFontSize = when (LocalRingoutThemeMode.current) {
        ThemeMode.Dark -> 40.sp
        ThemeMode.Light -> 58.sp
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.weight(1f, fill = false).padding(10.dp)) {
                RoomActivitySummary(
                    icon = Res.drawable.social_room_activity_days,
                    text = activityDaysText,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box(modifier = Modifier.weight(1f, fill = false).padding(10.dp)) {
                RoomActivitySummary(
                    icon = Res.drawable.social_room_activity_time,
                    text = activityTimeText,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "다음 일정",
                    color = colors.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                    ),
                )
                if (!nextScheduleText.isNullOrBlank()) {
                    Text(
                        text = nextScheduleText,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }

            if (nextScheduleText.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "예정된 일정이 없어요.",
                    color = colors.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (!remainingTimeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 73.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        BasicText(
                            text = remainingTimeText,
                            modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                            style = MaterialTheme.typography.displayLarge.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = countdownFontSize,
                                lineHeight = 1.2.em,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 14.sp,
                                maxFontSize = countdownFontSize,
                                stepSize = 1.sp,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "남음",
                            modifier = Modifier.alignByBaseline(),
                            color = colors.content,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                lineHeight = 22.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "일정 · 다크", widthDp = 402)
@Composable
private fun RoomHomeSchedulePreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeSchedule(
            activityDaysText = "매일",
            activityTimeText = "오전 06:00",
            nextScheduleText = "오늘 오전 06:00",
            remainingTimeText = "00:18:24",
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 20.dp),
        )
    }
}

@Preview(name = "일정 · 라이트", widthDp = 402)
@Composable
private fun RoomHomeScheduleLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeSchedule(
            activityDaysText = "매일",
            activityTimeText = "오전 06:00",
            nextScheduleText = "오늘 오전 06:00",
            remainingTimeText = "00:18:24",
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 20.dp),
        )
    }
}

@Preview(name = "긴 남은 시간 · 작은 화면", widthDp = 320, fontScale = 1.5f)
@Composable
private fun RoomHomeScheduleLongDurationPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeSchedule(
            activityDaysText = "월 · 수 · 금",
            activityTimeText = "오전 06:00",
            nextScheduleText = "내일 오전 06:00",
            remainingTimeText = "1일 1시간 17분",
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 20.dp),
        )
    }
}

@Preview(name = "예정된 일정 없음", widthDp = 402)
@Composable
private fun RoomHomeScheduleEmptyPreview() {
    RingoutTheme {
        RoomHomeSchedule(
            activityDaysText = "매일",
            activityTimeText = "오전 06:00",
            nextScheduleText = null,
            remainingTimeText = null,
            modifier = Modifier.background(roomHomeColors().background).padding(horizontal = 20.dp),
        )
    }
}
