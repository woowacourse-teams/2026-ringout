package com.joon.ringout.presentation.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.presentation.home.model.HomeAlarm
import com.joon.ringout.presentation.toTwelveHourDisplay
import com.joon.ringout.ringoutColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.home_add
import ringout.shared.generated.resources.home_toggle_off
import ringout.shared.generated.resources.home_toggle_on_dark
import ringout.shared.generated.resources.home_toggle_on_light
import kotlin.time.Clock

@Composable
internal fun AlarmListHeader(
    nextAlarmDescription: String,
    onMyPageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = homeAlarmColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(65.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "알람",
                color = colors.primaryText,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Black,
                ),
            )
            Text(
                text = nextAlarmDescription,
                color = colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 21.6.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        HomeMyPageButton(onClick = onMyPageClick)
    }
}

@Composable
internal fun HomeMyPageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "마이페이지 열기",
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(HomeProfileIconResource),
                contentDescription = "마이페이지",
                modifier = Modifier.size(width = 18.8333.dp, height = 20.4583.dp),
            )
        }
    }
}

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
                        color = colors.secondaryText,
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
                        color = colors.primaryText,
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
                .height(35.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.width(183.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AlarmInfoColumn(
                    label = "목적지",
                    value = alarm.destination,
                    valueColor = colors.primaryText,
                    modifier = Modifier.width(70.dp),
                )
                AlarmInfoColumn(
                    label = "알람 간격",
                    value = "${alarm.timeLimitMinutes}분",
                    valueColor = MaterialTheme.colorScheme.primary,
                    horizontalAlignment = Alignment.End,
                )
            }

            Spacer(Modifier.weight(1f))

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
            tint = colors.primaryText,
            modifier = Modifier.size(width = 14.dp, height = 18.dp),
        )
    }
}

@Composable
internal fun rememberActiveAlarmMissionRemainingSeconds(
    mission: ActiveAlarmMission,
    onExpired: () -> Unit,
): Long {
    var remainingSeconds by remember(
        mission.alarmId,
        mission.expiresAtEpochMillis,
    ) {
        mutableStateOf(
            remainingAlarmMissionSeconds(
                expiresAtEpochMillis = mission.expiresAtEpochMillis,
                nowEpochMillis = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }
    val currentOnExpired by rememberUpdatedState(onExpired)

    LaunchedEffect(
        mission.alarmId,
        mission.expiresAtEpochMillis,
    ) {
        while (true) {
            val nowEpochMillis = Clock.System.now().toEpochMilliseconds()
            remainingSeconds = remainingAlarmMissionSeconds(
                expiresAtEpochMillis = mission.expiresAtEpochMillis,
                nowEpochMillis = nowEpochMillis,
            )
            if (remainingSeconds == 0L) {
                currentOnExpired()
                break
            }

            val remainingMillis =
                (mission.expiresAtEpochMillis - nowEpochMillis).coerceAtLeast(1L)
            delay(remainingMillis.coerceAtMost(CountdownRefreshIntervalMillis))
        }
    }

    return remainingSeconds
}

@Composable
internal fun ActiveAlarmMissionRow(
    mission: ActiveAlarmMission,
    remainingSeconds: Long,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val countdown = formatAlarmMissionRemainingTime(remainingSeconds)
    val cardColor = MaterialTheme.colorScheme.primary
    val cardContentColor = MaterialTheme.ringoutColors.primaryActionContent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardColor)
            .clickable(
                role = Role.Button,
                onClickLabel = "진행 중인 알람 지도 열기",
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "목적지 ${mission.destinationName}, 다음 알람까지 $countdown"
            }
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "목적지",
                    color = cardContentColor,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = mission.destinationName,
                    color = cardContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "다음 알람까지",
                    color = cardContentColor,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = countdown,
                    color = cardContentColor,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
            }
        }
    }
}

internal fun remainingAlarmMissionSeconds(
    expiresAtEpochMillis: Long,
    nowEpochMillis: Long,
): Long {
    val remainingMillis = expiresAtEpochMillis - nowEpochMillis
    return if (remainingMillis <= 0L) {
        0L
    } else {
        ((remainingMillis - 1L) / MillisecondsPerSecond) + 1L
    }
}

internal fun formatAlarmMissionRemainingTime(remainingSeconds: Long): String {
    val safeRemainingSeconds = remainingSeconds.coerceAtLeast(0L)
    val minutes = safeRemainingSeconds / SecondsPerMinute
    val seconds = safeRemainingSeconds % SecondsPerMinute
    return "${minutes.toString().padStart(2, '0')}:" +
        seconds.toString().padStart(2, '0')
}

@Composable
private fun AlarmInfoColumn(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    labelColor: Color? = null,
) {
    val colors = homeAlarmColors()

    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            text = label,
            color = labelColor ?: colors.secondaryText,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
    }
}

@Composable
private fun AlarmToggle(
    alarmTime: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val isDarkTheme = LocalRingoutThemeMode.current == ThemeMode.Dark
    val painter = when {
        !enabled -> painterResource(Res.drawable.home_toggle_off)
        isDarkTheme -> painterResource(Res.drawable.home_toggle_on_dark)
        else -> painterResource(Res.drawable.home_toggle_on_light)
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = "$alarmTime 알람"
            }
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = onEnabledChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(width = 48.dp, height = 28.dp),
        )
    }
}

@Composable
internal fun HomeAddAlarmButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(
                role = Role.Button,
                onClickLabel = "알람 추가",
                onClick = onClick,
            )
            .semantics {
                contentDescription = "알람 추가"
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.home_add),
            contentDescription = null,
            modifier = Modifier.size(width = 22.17.dp, height = 24.87.dp),
        )
    }
}

@Composable
internal fun homeAlarmColors(): HomeAlarmColors {
    val isDarkTheme = LocalRingoutThemeMode.current == ThemeMode.Dark
    return if (isDarkTheme) {
        HomeAlarmColors(
            screenBackground = Color.Black,
            cardBackground = Color(0xFF171717),
            primaryText = Color.White,
            secondaryText = Color(0xFF8C8C8C),
        )
    } else {
        HomeAlarmColors(
            screenBackground = Color.White,
            cardBackground = Color(0xFFF5F5F5),
            primaryText = Color(0xFF111827),
            secondaryText = Color(0xFF6B7280),
        )
    }
}

internal data class HomeAlarmColors(
    val screenBackground: Color,
    val cardBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
)

internal val SecondaryText = Color(0xFF6E756F)
internal val Orange = Color(0xFFFF6D2E)

private const val CountdownRefreshIntervalMillis = 1_000L
private const val MillisecondsPerSecond = 1_000L
private const val SecondsPerMinute = 60L

@Preview(widthDp = 360, heightDp = 65)
@Composable
private fun HomeMyPageButtonPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.CenterEnd,
        ) {
            HomeMyPageButton(onClick = {})
        }
    }
}

@Preview
@Composable
private fun DarkActiveAlarmMissionRowPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        Box(
            modifier = Modifier
                .background(Color.Black)
                .padding(20.dp),
        ) {
            ActiveAlarmMissionRow(
                mission = previewActiveAlarmMission(),
                remainingSeconds = 12 * SecondsPerMinute,
            )
        }
    }
}

@Preview
@Composable
private fun LightActiveAlarmMissionRowPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        Box(
            modifier = Modifier
                .background(Color.White)
                .padding(20.dp),
        ) {
            ActiveAlarmMissionRow(
                mission = previewActiveAlarmMission(),
                remainingSeconds = 12 * SecondsPerMinute,
            )
        }
    }
}

@Preview
@Composable
private fun DarkAlarmRowPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        Box(
            modifier = Modifier
                .background(Color.Black)
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

@Preview
@Composable
private fun LightAlarmRowPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        Box(
            modifier = Modifier
                .background(Color.White)
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

private fun previewActiveAlarmMission() = ActiveAlarmMission(
    alarmId = "preview-active-alarm",
    destinationName = "헬스장",
    limitMinutes = 12,
    expiresAtEpochMillis = 0L,
)
