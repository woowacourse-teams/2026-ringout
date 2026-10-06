package com.joon.ringout.presentation.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode

@Composable
internal fun homeAlarmColors(): HomeAlarmColors {
    val isDarkTheme = LocalRingoutThemeMode.current == ThemeMode.Dark
    return if (isDarkTheme) {
        HomeAlarmColors(
            screenBackground = Color.Black,
            cardBackground = Color(0xFF171717),
            primaryText = Color.White,
            secondaryText = Color(0xFF8C8C8C),
            cardPrimaryText = Color(0xFFF5F5F6),
            cardSecondaryText = Color(0xFFA7A9B0),
        )
    } else {
        HomeAlarmColors(
            screenBackground = Color.White,
            cardBackground = Color(0xFFF5F5F5),
            primaryText = Color(0xFF111827),
            secondaryText = Color(0xFF6B7280),
            cardPrimaryText = Color(0xFF111827),
            cardSecondaryText = Color(0xFF6B7280),
        )
    }
}

internal data class HomeAlarmColors(
    val screenBackground: Color,
    val cardBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val cardPrimaryText: Color,
    val cardSecondaryText: Color,
)

