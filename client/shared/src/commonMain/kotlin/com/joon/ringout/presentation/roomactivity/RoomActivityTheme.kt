package com.joon.ringout.presentation.roomactivity

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roomhome.roomHomeColors

// Figma 493:10727, 493:10810. 그 외 표면·텍스트·타임라인은 기존 모임 테마를 공유한다.
private val DarkMemberArrival = Color(0xFF34C759)

@Composable
internal fun RoomActivityMemberStatus.color(): Color = when (this) {
    RoomActivityMemberStatus.Moving -> MaterialTheme.colorScheme.primary
    RoomActivityMemberStatus.Arrived -> if (LocalRingoutThemeMode.current == ThemeMode.Dark) {
        DarkMemberArrival
    } else roomHomeColors().successText
    else -> roomHomeColors().secondary
}
