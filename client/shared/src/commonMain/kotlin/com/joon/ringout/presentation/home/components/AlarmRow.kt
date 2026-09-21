package com.joon.ringout.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.home.model.HomeAlarm
import com.joon.ringout.presentation.toTwelveHourDisplay
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun AlarmRow(
    alarm: HomeAlarm,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = homeAlarmColors()
    val displayTime = alarm.time.toTwelveHourDisplay()
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.cardBackground)
            .clickable(
                role = Role.Button,
                onClickLabel = "알람 편집",
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.days,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayTime.period,
                        modifier = Modifier.alignByBaseline(),
                        color = colors.cardSecondaryText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = displayTime.time,
                        modifier = Modifier.alignByBaseline(),
                        color = colors.cardPrimaryText,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }

            AlarmToggle(
                alarmTime = displayTime.time,
                enabled = alarm.isEnabled,
                onEnabledChange = onEnabledChange,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 39.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlarmDestinationInfo(
                destination = alarm.destination,
                intervalMinutes = alarm.timeLimitMinutes,
                modifier = Modifier.weight(1f),
            )
            AlarmDeleteButton(onClick = onDelete)
        }
    }
}

@Composable
private fun AlarmDeleteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = homeAlarmColors()

    Box(
        modifier = modifier
            .size(24.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = "알람 삭제",
                onClick = onClick,
            )
            .semantics {
                contentDescription = "알람 삭제"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(HomeAlarmDeleteIconResource),
            contentDescription = null,
            tint = colors.cardSecondaryText,
            modifier = Modifier.size(width = 14.dp, height = 18.dp),
        )
    }
}

@Preview(widthDp = 402)
@Composable
private fun DarkAlarmRowPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        Box(
            modifier = Modifier
                .background(homeAlarmColors().screenBackground)
                .padding(20.dp),
        ) {
            AlarmRow(
                alarm = previewAlarm(isEnabled = true),
                onClick = {},
                onEnabledChange = {},
                onDelete = {},
            )
        }
    }
}

@Preview(widthDp = 402)
@Composable
private fun LightAlarmRowPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        Box(
            modifier = Modifier
                .background(homeAlarmColors().screenBackground)
                .padding(20.dp),
        ) {
            AlarmRow(
                alarm = previewAlarm(isEnabled = false),
                onClick = {},
                onEnabledChange = {},
                onDelete = {},
            )
        }
    }
}

private fun previewAlarm(isEnabled: Boolean) = HomeAlarm(
    id = "preview-alarm",
    time = "06:20",
    days = "주말",
    destination = "헬스장",
    timeLimitMinutes = 12,
    isEnabled = isEnabled,
    selectedDays = listOf("토", "일"),
)

@Preview
@Composable
private fun AlarmDeleteButtonPreview() {
    RingoutTheme {
        AlarmDeleteButton(onClick = {})
    }
}
