package com.joon.ringout.presentation.activemission.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class ActiveAlarmTrackingColors(
    val screenChrome: Color,
    val headerBackground: Color,
    val headerBorder: Color,
    val panelShadow: Color,
    val panelContent: Color,
    val forceEndProgressFill: Color,
)

internal fun activeAlarmTrackingColors(): ActiveAlarmTrackingColors =
    ActiveAlarmTrackingColors(
        screenChrome = Color.Black,
        headerBackground = Color.White,
        headerBorder = Color(0xFFD0D0D0),
        panelShadow = Color.Black,
        panelContent = Color(0xFFF5F5F6),
        forceEndProgressFill = Color(0xFFFF0000),
    )
