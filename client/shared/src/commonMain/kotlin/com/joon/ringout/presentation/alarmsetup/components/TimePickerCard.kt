package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(259.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(colors.timeCard),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(247.dp)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(
                space = 8.dp,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimePickerWheel(
                values = AmPmValues,
                selectedValue = isAm,
                onValueChange = onAmPmChange,
                valueLabel = { value -> if (value) "오전" else "오후" },
                accessibilityLabel = "오전 오후",
                selectedColor = colors.accent,
                unselectedColor = colors.supportingText,
                selectedFontSize = 24.sp,
                unselectedFontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                isLooping = false,
                modifier = Modifier.width(68.dp),
            )

            Row(
                modifier = Modifier.width(240.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimePickerWheel(
                    values = HourValues,
                    selectedValue = hour.coerceIn(1, 12),
                    onValueChange = onHourChange,
                    valueLabel = Int::twoDigits,
                    accessibilityLabel = "시",
                    selectedColor = colors.accent,
                    unselectedColor = colors.secondaryText,
                    selectedFontSize = 72.sp,
                    unselectedFontSize = 62.sp,
                    fontWeight = FontWeight.Bold,
                    isLooping = true,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = ":",
                    color = colors.accent,
                    maxLines = 1,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        lineHeight = 70.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                TimePickerWheel(
                    values = MinuteValues,
                    selectedValue = minute.coerceIn(0, 59),
                    onValueChange = onMinuteChange,
                    valueLabel = Int::twoDigits,
                    accessibilityLabel = "분",
                    selectedColor = colors.accent,
                    unselectedColor = colors.secondaryText,
                    selectedFontSize = 72.sp,
                    unselectedFontSize = 62.sp,
                    fontWeight = FontWeight.Bold,
                    isLooping = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

private val AmPmValues = listOf(true, false)
private val HourValues = (1..12).toList()
private val MinuteValues = (0..59).toList()

@Preview(widthDp = 402, heightDp = 299)
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
