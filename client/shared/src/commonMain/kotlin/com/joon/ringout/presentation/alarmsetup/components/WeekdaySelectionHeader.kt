package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
internal fun WeekdaySelectionHeader(
    selectedDays: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()
    val summary = weekdaySummary(selectedDays)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlarmSetupSectionLabel(
            text = "요일",
            color = colors.primaryText,
        )
        if (summary.isNotEmpty()) {
            Text(
                text = summary,
                color = colors.accent,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Preview(widthDp = 362, heightDp = 38)
@Composable
private fun WeekdaySelectionHeaderPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmSetupPreviewSurface {
            WeekdaySelectionHeader(
                selectedDays = listOf("월", "화", "수", "금"),
            )
        }
    }
}
