package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode

@Composable
fun SaveAlarmButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = alarmSetupColors()

    Box(
        modifier = modifier
            .size(width = 252.dp, height = 44.dp)
            .clip(RoundedCornerShape(500.dp))
            .background(if (enabled) colors.accent else colors.supportingText)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "알람 저장",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (enabled) "저장" else "목적지를 설정해주세요",
            color = colors.selectedContent,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Preview(widthDp = 292, showBackground = true)
@Composable
private fun SaveAlarmButtonPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmSetupPreviewSurface {
            SaveAlarmButton(enabled = true, onClick = {})
        }
    }
}

@Preview(widthDp = 292, showBackground = true)
@Composable
private fun SaveAlarmButtonDisabledPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        AlarmSetupPreviewSurface {
            SaveAlarmButton(enabled = false, onClick = {})
        }
    }
}
