package com.joon.ringout.presentation.destination.component

import androidx.compose.ui.graphics.Color

internal data class DestinationManagementColors(
    val surface: Color,
    val border: Color,
    val title: Color,
    val itemText: Color,
    val secondaryAction: Color,
    val primaryAction: Color,
    val scrim: Color,
    val shadow: Color,
    val destructive: Color,
    val confirmTitle: Color,
    val confirmBody: Color,
    val cancelAction: Color,
    val addressText: Color,
)

internal val DestinationManagementPalette = DestinationManagementColors(
    addressText = Color(0xFF8C8C8C),
    destructive = Color(0xFFFF0000),
    confirmTitle = Color(0xFF1F2937),
    confirmBody = Color(0xFF6B7280),
    cancelAction = Color(0xFF808080),
    surface = Color.White,
    border = Color(0xFFD0D0D0),
    title = Color.Black,
    itemText = Color(0xFF0F1012),
    secondaryAction = Color(0xFFA7A9B0),
    primaryAction = Color(0xFFFF6D2E),
    scrim = Color.Black.copy(alpha = 0.5f),
    shadow = Color.Black.copy(alpha = 0.25f),
)
