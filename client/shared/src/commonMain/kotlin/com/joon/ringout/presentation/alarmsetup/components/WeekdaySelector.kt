package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.common.WeekdayOrder

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
            .heightIn(min = 119.dp)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WeekdaySelectionHeader(selectedDays = selectedDays)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
        ) {
            val textStyle = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val minimumChipWidth = with(density) {
                WeekdayOrder.maxOf { measurer.measure(it, textStyle, softWrap = false).size.width }.toDp() + 10.dp
            }
            val idealChipWidth = maxOf(37.dp, minimumChipWidth)
            val daySpacing = (
                (maxWidth - idealChipWidth * WeekdayOrder.size) / (WeekdayOrder.size - 1)
            ).coerceIn(4.dp, 10.dp)
            // 각 칩의 픽셀 반올림이 누적되어 마지막 요일만 밀려나지 않도록 내림한다.
            val singleRowChipWidth = with(density) {
                ((constraints.maxWidth - daySpacing.roundToPx() * (WeekdayOrder.size - 1)) / WeekdayOrder.size).toDp()
            }
            val chipWidth = maxOf(minimumChipWidth, minOf(idealChipWidth, singleRowChipWidth))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(daySpacing),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                WeekdayOrder.forEach { day ->
                    val selected = day in selectedDays
                    Box(
                        modifier = Modifier
                            .width(chipWidth)
                            .heightIn(min = 51.dp)
                            .toggleable(
                                value = selected,
                                role = Role.Checkbox,
                                onValueChange = { onDayClick(day) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 32.dp)
                                .background(
                                    color = if (selected) {
                                        colors.accent
                                    } else {
                                        colors.unselectedDayBackground
                                    },
                                    shape = RoundedCornerShape(5.dp),
                                )
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = day,
                                color = if (selected) {
                                    colors.selectedContent
                                } else {
                                    colors.unselectedDayText
                                },
                                style = textStyle,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 402)
@Preview(name = "큰 글자 · 작은 화면", widthDp = 320, fontScale = 2f)
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
