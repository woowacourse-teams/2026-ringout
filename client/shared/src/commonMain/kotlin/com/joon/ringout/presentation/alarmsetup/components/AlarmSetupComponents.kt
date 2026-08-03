package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.toTwelveHourDisplay
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.alarm_setup_back_dark
import ringout.shared.generated.resources.alarm_setup_back_light
import ringout.shared.generated.resources.alarm_setup_chevron_dark
import ringout.shared.generated.resources.alarm_setup_chevron_light
import ringout.shared.generated.resources.alarm_setup_pin
import ringout.shared.generated.resources.alarm_setup_sound
import ringout.shared.generated.resources.alarm_setup_thumb_dark
import ringout.shared.generated.resources.alarm_setup_thumb_light

private val RingoutOrange = Color(0xFFFF6D2E)
private val WeekdayOrder = listOf("월", "화", "수", "목", "금", "토", "일")

internal data class AlarmSetupColors(
    val background: Color,
    val primaryText: Color,
    val sectionLabel: Color,
    val supportingText: Color,
    val timeCard: Color,
    val unselectedDayBackground: Color,
    val unselectedDayText: Color,
    val inactiveTrack: Color,
    val backIcon: DrawableResource,
    val chevronIcon: DrawableResource,
    val sliderThumb: DrawableResource,
)

@Composable
internal fun alarmSetupColors(): AlarmSetupColors =
    if (LocalRingoutThemeMode.current == ThemeMode.Dark) {
        AlarmSetupColors(
            background = Color.Black,
            primaryText = Color.White,
            sectionLabel = Color(0xFF8C8C8C),
            supportingText = Color(0xFF8C8C8C),
            timeCard = Color.Black,
            unselectedDayBackground = Color(0xFF8C8C8C),
            unselectedDayText = Color.White,
            inactiveTrack = Color(0xFF8C8C8C),
            backIcon = Res.drawable.alarm_setup_back_dark,
            chevronIcon = Res.drawable.alarm_setup_chevron_dark,
            sliderThumb = Res.drawable.alarm_setup_thumb_dark,
        )
    } else {
        AlarmSetupColors(
            background = Color.White,
            primaryText = Color(0xFF111827),
            sectionLabel = Color(0xFF374151),
            supportingText = Color(0xFF6B7280),
            timeCard = Color(0xFFF5F5F5),
            unselectedDayBackground = Color(0xFFE5E7EB),
            unselectedDayText = Color(0xFF111827),
            inactiveTrack = Color(0xFFE5E7EB),
            backIcon = Res.drawable.alarm_setup_back_light,
            chevronIcon = Res.drawable.alarm_setup_chevron_light,
            sliderThumb = Res.drawable.alarm_setup_thumb_light,
        )
    }

@Composable
fun SetupBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(41.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "이전 화면으로 이동",
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(colors.backIcon),
                contentDescription = "뒤로",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun TimePickerCard(
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()
    val displayTime = time.toTwelveHourDisplay()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(149.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(colors.timeCard)
            .clickable(
                role = Role.Button,
                onClickLabel = "알람 시간 변경",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayTime.period,
                color = colors.supportingText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = displayTime.time,
                color = colors.primaryText,
                maxLines = 1,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp,
                    lineHeight = 77.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
fun WeekdaySelector(
    selectedDays: List<String>,
    onDayClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(95.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("요일", colors.sectionLabel)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(51.dp)
                .padding(horizontal = 10.dp),
        ) {
            val idealChipWidth = 37.dp
            val daySpacing = (
                (maxWidth - idealChipWidth * WeekdayOrder.size) /
                    (WeekdayOrder.size - 1)
            ).coerceIn(4.dp, 10.dp)
            val chipWidth = minOf(
                idealChipWidth,
                (maxWidth - daySpacing * (WeekdayOrder.size - 1)) /
                    WeekdayOrder.size,
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(daySpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WeekdayOrder.forEach { day ->
                    val selected = day in selectedDays
                    Box(
                        modifier = Modifier
                            .width(chipWidth)
                            .fillMaxHeight()
                            .toggleable(
                                value = selected,
                                role = Role.Checkbox,
                                onValueChange = { onDayClick(day) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = chipWidth, height = 32.dp)
                                .background(
                                    color = if (selected) {
                                        RingoutOrange
                                    } else {
                                        colors.unselectedDayBackground
                                    },
                                    shape = RoundedCornerShape(5.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = day,
                                color = if (selected) {
                                    Color.White
                                } else {
                                    colors.unselectedDayText
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DestinationCard(
    destination: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(123.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("목적지", colors.sectionLabel)
        SupportingLabel(
            text = "알람을 끄고 이동할 곳을 선택해주세요",
            color = colors.supportingText,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "목적지 변경",
                    onClick = onClick,
                )
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.alarm_setup_pin),
                    contentDescription = null,
                    modifier = Modifier.size(width = 18.dp, height = 22.dp),
                )
            }
            Text(
                text = destination,
                modifier = Modifier.weight(1f),
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Image(
                painter = painterResource(colors.chevronIcon),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 12.dp, height = 17.dp)
                    .graphicsLayer { scaleX = -1f },
            )
        }
    }
}

@Composable
fun LimitTimeCard(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()
    val sliderMinutes = minutes.coerceIn(MinLimitMinutes, MaxLimitMinutes)
    val progress =
        (sliderMinutes - MinLimitMinutes).toFloat() /
            (MaxLimitMinutes - MinLimitMinutes).toFloat()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(159.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(89.dp)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel("제한 시간", colors.sectionLabel)
                SupportingLabel(
                    text = "목적지까지 이동할 제한 시간을 설정해주세요",
                    color = colors.supportingText,
                )
            }
            Text(
                text = "${sliderMinutes}분",
                color = RingoutOrange,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Slider(
            value = sliderMinutes.toFloat(),
            onValueChange = { value ->
                onMinutesChange(
                    value.roundToInt().coerceIn(MinLimitMinutes, MaxLimitMinutes),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            valueRange = MinLimitMinutes.toFloat()..MaxLimitMinutes.toFloat(),
            steps = MaxLimitMinutes - MinLimitMinutes - 1,
            thumb = {
                Image(
                    painter = painterResource(colors.sliderThumb),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            track = {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                ) {
                    val trackSlotWidth = maxWidth
                    val fullTrackWidth = trackSlotWidth + SliderThumbSize
                    Box(
                        modifier = Modifier
                            .requiredWidth(fullTrackWidth)
                            .fillMaxHeight()
                            .background(
                                color = colors.inactiveTrack,
                                shape = RoundedCornerShape(15.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(SliderThumbRadius + trackSlotWidth * progress)
                                .fillMaxHeight()
                                .background(
                                    color = RingoutOrange,
                                    shape = RoundedCornerShape(15.dp),
                                ),
                        )
                    }
                }
            },
        )
    }
}

@Composable
fun AlarmSoundCard(
    soundName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(99.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("알람음", colors.sectionLabel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "알람음 변경",
                    onClick = onClick,
                )
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.alarm_setup_sound),
                    contentDescription = null,
                    modifier = Modifier.size(width = 22.dp, height = 22.dp),
                )
            }
            Text(
                text = soundName,
                modifier = Modifier.weight(1f),
                color = colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Image(
                painter = painterResource(colors.chevronIcon),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 12.dp, height = 17.dp)
                    .graphicsLayer { scaleX = -1f },
            )
        }
    }
}

@Composable
fun SaveAlarmButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 252.dp, height = 44.dp)
            .clip(RoundedCornerShape(500.dp))
            .background(RingoutOrange)
            .clickable(
                role = Role.Button,
                onClickLabel = "알람 저장",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "저장",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        maxLines = 1,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 16.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Normal,
        ),
    )
}

@Composable
private fun SupportingLabel(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        maxLines = 1,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Normal,
        ),
    )
}

internal fun weekdaySummary(days: List<String>): String =
    when (days.toSet()) {
        setOf("월", "화", "수", "목", "금") -> "평일"
        setOf("토", "일") -> "주말"
        WeekdayOrder.toSet() -> "매일"
        emptySet<String>() -> "선택 안 함"
        else -> WeekdayOrder.filter { it in days }.joinToString(" ")
    }

private const val MinLimitMinutes = 1
private const val MaxLimitMinutes = 30
private val SliderThumbSize = 16.dp
private val SliderThumbRadius = SliderThumbSize / 2

@Preview
@Composable
private fun AlarmSetupComponentsDarkPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        Column(
            modifier = Modifier
                .background(Color.Black)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TimePickerCard("06:20", {})
            WeekdaySelector(listOf("월", "화", "수", "금"), {})
            DestinationCard("집 앞에 수원천", {})
            LimitTimeCard(13, {})
            AlarmSoundCard("Ring Ring Ring", {})
            SaveAlarmButton({})
        }
    }
}

@Preview
@Composable
private fun AlarmSetupComponentsLightPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        Column(
            modifier = Modifier
                .background(Color.White)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TimePickerCard("06:20", {})
            WeekdaySelector(listOf("월", "화", "수", "금"), {})
            DestinationCard("집 앞에 수원천", {})
            LimitTimeCard(13, {})
            AlarmSoundCard("Ring Ring Ring", {})
            SaveAlarmButton({})
        }
    }
}
