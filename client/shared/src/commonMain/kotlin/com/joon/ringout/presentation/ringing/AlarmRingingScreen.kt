package com.joon.ringout.presentation.ringing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.joon.ringout.presentation.ringing.components.AlarmBellVisual
import com.joon.ringout.presentation.ringing.components.AlarmDismissButton
import com.joon.ringout.presentation.ringing.components.AlarmTimeDetails
import com.joon.ringout.presentation.ringing.components.alarmRingingColors

@Composable
fun AlarmRingingScreen(
    alarmTime: String,
    dateText: String,
    limitMinutes: Int,
    destinationName: String,
    onDismissAndNavigateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmRingingColors()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        AlarmBellVisual()
        Text(
            text = dateText,
            color = colors.primaryText,
            maxLines = 1,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        AlarmTimeDetails(
            alarmTime = formatAlarmDisplayTime(alarmTime),
            limitMinutes = limitMinutes,
            destinationName = destinationName,
        )
        AlarmDismissButton(onClick = onDismissAndNavigateClick)
    }
}

internal fun formatAlarmDisplayTime(value: String): String {
    val parts = value.trim().split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 }
    val minute = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 }
    return if (hour != null && minute != null) {
        "$hour:${minute.toString().padStart(2, '0')}"
    } else {
        value.ifBlank { "--:--" }
    }
}

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun AlarmRingingScreenDarkPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmRingingScreen(
            alarmTime = "07:00",
            dateText = "2026년 7월 23일 목요일",
            limitMinutes = 30,
            destinationName = "강남역 2번 출구",
            onDismissAndNavigateClick = {},
        )
    }
}
