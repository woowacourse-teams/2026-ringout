package com.joon.ringout.presentation.mypage.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors

@Immutable
internal data class MyPageColors(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val calendarSurface: Color,
    val calendarBorder: Color,
    val sectionSurface: Color,
    val toggleTrack: Color,
    val toggleInactiveContent: Color,
)

@Composable
internal fun myPageColors(): MyPageColors =
    if (LocalRingoutThemeMode.current == ThemeMode.Dark) {
        MyPageColors(
            background = Color(0xFF0F1012),
            primaryText = Color(0xFFF5F5F6),
            secondaryText = Color(0xFFA7A9B0),
            calendarSurface = Color(0xFF181A1E),
            calendarBorder = Color(0xFF34363D),
            sectionSurface = Color(0xFF1A1A1A),
            toggleTrack = Color(0xFF8C8C8C),
            toggleInactiveContent = Color(0xFFA7A9B0),
        )
    } else {
        MyPageColors(
            background = MaterialTheme.colorScheme.background,
            primaryText = MaterialTheme.colorScheme.onBackground,
            secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
            calendarSurface = MaterialTheme.colorScheme.surface,
            calendarBorder = MaterialTheme.colorScheme.outline,
            sectionSurface = MaterialTheme.ringoutColors.elevatedSurface,
            toggleTrack = MaterialTheme.colorScheme.surfaceVariant,
            toggleInactiveContent = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
