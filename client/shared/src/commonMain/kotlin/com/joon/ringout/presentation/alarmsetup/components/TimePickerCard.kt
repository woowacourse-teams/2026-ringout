package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
fun TimePickerCard(
    isAm: Boolean,
    hour: Int,
    minute: Int,
    onAmPmChange: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 259.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(colors.timeCard),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxWidth < 336.dp
        val selectedTimeFontSize = if (compact) 50.sp else 72.sp
        val unselectedTimeFontSize = if (compact) 42.sp else 62.sp

        val selectedAmPmFontSize = if (compact) 18.sp else 24.sp
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer(cacheSize = 32)
        val timeStyle = MaterialTheme.typography.displayLarge.copy(
            fontSize = selectedTimeFontSize,
            lineHeight = selectedTimeFontSize,
            fontWeight = FontWeight.Bold,
        )
        val amPmStyle = timeStyle.copy(
            fontSize = selectedAmPmFontSize,
            lineHeight = selectedAmPmFontSize,
            fontWeight = FontWeight.Medium,
        )
        fun measuredWidth(labels: List<String>, style: TextStyle): Dp = with(density) {
            labels.maxOf { measurer.measure(it, style, softWrap = false).size.width }.toDp()
        }
        // 각 숫자의 최대 폭을 두 자리 모두에 확보한다. Android의 비선형 글자 배율도 실제 측정에 반영된다.
        val numberWidth = measuredWidth((0..9).map { "$it$it" }, timeStyle) + 8.dp
        val amPmWidth = maxOf(
            if (compact) 54.dp else 68.dp,
            measuredWidth(listOf("오전", "오후"), amPmStyle) + 8.dp,
        )
        val colonWidth = measuredWidth(listOf(":"), timeStyle)
        val itemHeight = with(density) {
            measurer.measure("00", timeStyle, softWrap = false).size.height.toDp()
        }.coerceAtLeast(70.dp)
        val amPmItemHeight = with(density) {
            measurer.measure("오전", amPmStyle, softWrap = false).size.height.toDp()
        }.coerceAtLeast(70.dp)
        val horizontalPadding = if (compact) 8.dp else 10.dp
        val availableWidth = maxWidth - horizontalPadding * 2
        val stackNumbers = numberWidth * 2 + colonWidth > availableWidth

        val amPmWheel: @Composable () -> Unit = {
            TimePickerWheel(
                values = AmPmValues,
                selectedValue = isAm,
                onValueChange = onAmPmChange,
                valueLabel = { value -> if (value) "오전" else "오후" },
                accessibilityLabel = "오전 오후",
                selectedColor = colors.accent,
                unselectedColor = colors.supportingText,
                selectedFontSize = selectedAmPmFontSize,
                unselectedFontSize = if (compact) 14.sp else 18.sp,
                fontWeight = FontWeight.Medium,
                isLooping = false,
                hapticFeedbackType = HapticFeedbackType.SegmentTick,
                itemHeight = amPmItemHeight,
                modifier = Modifier.width(amPmWidth),
            )
        }
        val numberWheel: @Composable (Boolean, Modifier) -> Unit = { isHour, wheelModifier ->
            TimePickerWheel(
                values = if (isHour) HourValues else MinuteValues,
                selectedValue = if (isHour) hour.coerceIn(1, 12) else minute.coerceIn(0, 59),
                onValueChange = if (isHour) onHourChange else onMinuteChange,
                valueLabel = Int::twoDigits,
                accessibilityLabel = if (isHour) "시" else "분",
                selectedColor = colors.accent,
                unselectedColor = colors.secondaryText,
                selectedFontSize = selectedTimeFontSize,
                unselectedFontSize = unselectedTimeFontSize,
                fontWeight = FontWeight.Bold,
                isLooping = true,
                hapticFeedbackType = HapticFeedbackType.SegmentFrequentTick,
                itemHeight = itemHeight,
                modifier = wheelModifier,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(
                space = if (compact) 4.dp else 8.dp,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            amPmWheel()
            if (stackNumbers) {
                // 시/분을 나눠 배치할 때는 콜론 대신 단위로 구분한다.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AlarmSetupSectionLabel("시", colors.primaryText)
                    numberWheel(true, Modifier.fillMaxWidth())
                    AlarmSetupSectionLabel("분", colors.primaryText)
                    numberWheel(false, Modifier.fillMaxWidth())
                }
            } else {
                val numbersWidth = maxOf(if (compact) 0.dp else 240.dp, numberWidth * 2 + colonWidth)
                    .coerceAtMost(availableWidth)
                Row(modifier = Modifier.width(numbersWidth), verticalAlignment = Alignment.CenterVertically) {
                    numberWheel(true, Modifier.weight(1f))
                    Text(
                        text = ":",
                        color = colors.accent,
                        maxLines = 1,
                        style = timeStyle.copy(lineHeight = selectedTimeFontSize),
                    )
                    numberWheel(false, Modifier.weight(1f))
                }
            }
        }
    }
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

private val AmPmValues = listOf(true, false)
private val HourValues = (1..12).toList()
private val MinuteValues = (0..59).toList()

@Preview(widthDp = 402)
@Preview(name = "큰 글자 · 작은 화면", widthDp = 320, fontScale = 2f)
@Composable
private fun TimePickerCardPreview() {
    var isAm by remember { mutableStateOf(true) }
    var hour by remember { mutableStateOf(6) }
    var minute by remember { mutableStateOf(20) }

    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmSetupPreviewSurface {
            TimePickerCard(
                isAm = isAm,
                hour = hour,
                minute = minute,
                onAmPmChange = { isAm = it },
                onHourChange = { hour = it },
                onMinuteChange = { minute = it },
            )
        }
    }
}
