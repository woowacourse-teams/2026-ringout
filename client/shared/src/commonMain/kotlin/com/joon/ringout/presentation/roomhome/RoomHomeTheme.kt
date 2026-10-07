package com.joon.ringout.presentation.roomhome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode

@Immutable
internal data class RoomHomeColors(
    val background: Color,
    val content: Color,
    val secondary: Color,
    val heroContent: Color,
    val heroOverlay: Color,
    val success: Color,
    val successText: Color,
    val failure: Color,
    val selectedDay: Color,
    val timeline: Color,
    val iconContent: Color,
    val dropdownSurface: Color,
    val dropdownContent: Color,
    val dropdownDivider: Color,
    val actionDialogPrimary: Color,
)

// Figma: 493:11437 / 493:11511 (dark), 465:9829 (light).
private val DarkRoomHomeColors = RoomHomeColors(
    background = Color(0xFF0F1012),
    content = Color.White,
    secondary = Color(0xFFA7A9B0),
    heroContent = Color.White,
    heroOverlay = Color.Black.copy(alpha = 0.2f),
    success = Color(0xFF10B981),
    successText = Color(0xFF10B981),
    failure = Color(0xFFFF2E2E),
    selectedDay = Color(0xFF22242A),
    timeline = Color(0xFFA7A9B0),
    iconContent = Color.White,
    dropdownSurface = Color(0xFF101113),
    dropdownContent = Color(0xFFF9FAFB),
    dropdownDivider = Color(0xFF4B4D52),
    actionDialogPrimary = Color(0xFFFF682B),
)

private val LightRoomHomeColors = RoomHomeColors(
    background = Color.White,
    content = Color(0xFF111827),
    secondary = Color(0xFF6B7280),
    heroContent = Color.White,
    heroOverlay = Color.Black.copy(alpha = 0.2f),
    success = Color(0xFF10B981),
    successText = Color(0xFF047857),
    failure = Color(0xFFFF2E2E),
    selectedDay = Color(0xFFECEEF1),
    timeline = Color(0xFFD1D5DB),
    iconContent = Color.White,
    dropdownSurface = Color(0xFF101113),
    dropdownContent = Color(0xFFF9FAFB),
    dropdownDivider = Color(0xFF4B4D52),
    actionDialogPrimary = Color(0xFFFF682B),
)

@Composable
internal fun roomHomeColors(): RoomHomeColors =
    if (LocalRingoutThemeMode.current == ThemeMode.Dark) DarkRoomHomeColors else LightRoomHomeColors
