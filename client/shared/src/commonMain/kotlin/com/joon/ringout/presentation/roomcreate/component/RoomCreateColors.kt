package com.joon.ringout.presentation.roomcreate.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors

@Immutable
internal data class RoomCreateColors(
    val inputSurface: Color,
    val placeholder: Color,
    val idleBorder: Color,
    val success: Color,
    val successBorder: Color,
    val error: Color,
)

@Composable
internal fun roomCreateColors(): RoomCreateColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = LocalRingoutThemeMode.current == ThemeMode.Dark
    return RoomCreateColors(
        inputSurface = scheme.surface,
        placeholder = MaterialTheme.ringoutColors.navigationInactiveContent,
        idleBorder = scheme.outline.copy(alpha = 0.38f),
        success = if (isDark) Color(0xFF10B981) else Color(0xFF087A56),
        successBorder = if (isDark) Color(0xFF10B981) else Color(0xFF087A56),
        error = scheme.primary,
    )
}
