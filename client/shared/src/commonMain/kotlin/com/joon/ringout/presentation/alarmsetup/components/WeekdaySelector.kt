package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

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
            .height(119.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WeekdaySelectionHeader(selectedDays = selectedDays)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(51.dp)
                .padding(horizontal = 10.dp),
        ) {
            val idealChipWidth = 37.dp
            val daySpacing = (
                (maxWidth - idealChipWidth * alarmSetupWeekdayOrder.size) /
                    (alarmSetupWeekdayOrder.size - 1)
            ).coerceIn(4.dp, 10.dp)
            val chipWidth = minOf(
                idealChipWidth,
                (maxWidth - daySpacing * (alarmSetupWeekdayOrder.size - 1)) /
                    alarmSetupWeekdayOrder.size,
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(daySpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                alarmSetupWeekdayOrder.forEach { day ->
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
                                        colors.accent
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
                                    colors.selectedContent
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

@Preview(widthDp = 402)
@Composable
private fun WeekdaySelectorPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmSetupPreviewSurface {
            WeekdaySelector(
                selectedDays = listOf("월", "화", "수", "금"),
                onDayClick = {},
            )
        }
    }
}
