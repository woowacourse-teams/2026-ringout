package com.joon.ringout.presentation.roommembermanagement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode

@Immutable
internal data class RoomMemberManagementColors(
    val background: Color,
    val title: Color,
    val nickname: Color,
    val joinedDate: Color,
)

// Figma: 493:10601 (dark), 465:9025 (light).
private val DarkRoomMemberManagementColors = RoomMemberManagementColors(
    background = Color(0xFF0F1012),
    title = Color.White,
    nickname = Color(0xFFF5F5F6),
    joinedDate = Color(0xFF6B7280),
)

private val LightRoomMemberManagementColors = RoomMemberManagementColors(
    background = Color.White,
    title = Color(0xFF111827),
    nickname = Color(0xFF111827),
    joinedDate = Color(0xFF6B7280),
)

@Composable
internal fun roomMemberManagementColors(): RoomMemberManagementColors =
    if (LocalRingoutThemeMode.current == ThemeMode.Dark) {
        DarkRoomMemberManagementColors
    } else {
        LightRoomMemberManagementColors
    }
