package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
internal fun AlarmSetupSectionLabel(
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
