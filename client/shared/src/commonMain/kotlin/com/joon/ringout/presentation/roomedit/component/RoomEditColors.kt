package com.joon.ringout.presentation.roomedit.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors

@Immutable
internal data class RoomEditColors(
    val inputSurface: Color,
    val placeholder: Color,
    val idleBorder: Color,
    val statusError: Color,
    val success: Color,
)

@Composable
internal fun roomEditColors(): RoomEditColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = LocalRingoutThemeMode.current == ThemeMode.Dark
    return RoomEditColors(
        inputSurface = scheme.surface,
        placeholder = MaterialTheme.ringoutColors.navigationInactiveContent,
        idleBorder = scheme.outline.copy(alpha = 0.38f),
        statusError = scheme.primary,
        success = if (isDark) Color(0xFF10B981) else Color(0xFF087A56),
    )
}
