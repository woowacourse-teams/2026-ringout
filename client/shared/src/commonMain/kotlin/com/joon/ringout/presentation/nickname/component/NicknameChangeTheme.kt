package com.joon.ringout.presentation.nickname.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode

@Immutable
internal data class NicknameChangeColors(
    val background: Color,
    val primaryText: Color,
    val inputSurface: Color,
    val inputText: Color,
    val inputIcon: Color,
    val inputIdleBorder: Color,
    val success: Color,
    val error: Color,
    val validInputBorder: Color,
    val invalidInputBorder: Color,
    val primaryAction: Color,
    val disabledAction: Color,
    val actionContent: Color,
)

@Composable
internal fun nicknameChangeColors(): NicknameChangeColors =
    if (LocalRingoutThemeMode.current == ThemeMode.Dark) {
        NicknameChangeColors(
            background = Color(0xFF0F1012),
            primaryText = Color(0xFFF5F5F6),
            inputSurface = Color.White,
            inputText = Color(0xFF6B7280),
            inputIcon = Color(0xFF9CA3AF),
            inputIdleBorder = Color.Transparent,
            success = Color(0xFF10B981),
            error = Color(0xFFEF4444),
            validInputBorder = Color(0xFF218A2C),
            invalidInputBorder = Color(0xFFC70909),
            primaryAction = Color(0xFFFF6D2E),
            disabledAction = Color(0xFFA7A9B0),
            actionContent = Color.White,
        )
    } else {
        NicknameChangeColors(
            background = MaterialTheme.colorScheme.background,
            primaryText = MaterialTheme.colorScheme.onBackground,
            inputSurface = MaterialTheme.colorScheme.surface,
            inputText = MaterialTheme.colorScheme.onSurfaceVariant,
            inputIcon = MaterialTheme.colorScheme.onSurfaceVariant,
            inputIdleBorder = MaterialTheme.colorScheme.outline,
            success = Color(0xFF087A56),
            error = Color(0xFFB91C1C),
            validInputBorder = Color(0xFF218A2C),
            invalidInputBorder = Color(0xFFC70909),
            primaryAction = MaterialTheme.colorScheme.primary,
            disabledAction = Color(0xFFA7A9B0),
            actionContent = Color.White,
        )
    }
