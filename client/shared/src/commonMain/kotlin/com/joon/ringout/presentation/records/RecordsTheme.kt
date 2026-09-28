package com.joon.ringout.presentation.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode

@Immutable
internal data class RecordsColors(
    val background: Color,
    val card: Color,
    val selectedDay: Color,
    val text: Color,
    val secondaryText: Color,
    val calendarSurface: Color,
    val calendarBorder: Color,
    val success: Color,
    val successSurface: Color,
    val failure: Color,
    val failureSurface: Color,
)

private val DarkRecordsColors = RecordsColors(
    background = Color(0xFF0F1012),
    card = Color(0xFF171717),
    selectedDay = Color(0xFF22242A),
    text = Color(0xFFF5F5F6),
    secondaryText = Color(0xFFA7A9B0),
    calendarSurface = Color(0xFF181A1E),
    calendarBorder = Color(0xFF34363D),
    success = Color(0xFF2EFF43),
    successSurface = Color(0xFF226E29),
    failure = Color(0xFFFF5252),
    failureSurface = Color.Black,
)

private val LightRecordsColors = RecordsColors(
    background = Color.White,
    card = Color(0xFFF5F5F5),
    selectedDay = Color(0xFFE9E9EC),
    text = Color(0xFF111827),
    secondaryText = Color(0xFF6B7280),
    calendarSurface = Color.White,
    calendarBorder = Color(0xFFD1D5DB),
    success = Color(0xFF226E29),
    successSurface = Color(0xFFDCEEDC),
    failure = Color(0xFFB3261E),
    failureSurface = Color(0xFFFCE4E4),
)

@Composable
internal fun recordsColors(): RecordsColors =
    if (LocalRingoutThemeMode.current == ThemeMode.Dark) DarkRecordsColors else LightRecordsColors
