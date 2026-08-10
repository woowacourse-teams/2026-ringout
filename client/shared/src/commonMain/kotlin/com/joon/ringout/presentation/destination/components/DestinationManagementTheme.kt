package com.joon.ringout.presentation.destination.components

import androidx.compose.ui.graphics.Color

internal data class DestinationManagementColors(
    val surface: Color,
    val title: Color,
    val itemText: Color,
    val secondaryAction: Color,
    val primaryAction: Color,
    val scrim: Color,
    val shadow: Color,
)

internal val DestinationManagementPalette = DestinationManagementColors(
    surface = Color.White,
    title = Color.Black,
    itemText = Color(0xFF0F1012),
    secondaryAction = Color(0xFFA7A9B0),
    primaryAction = Color(0xFFFF6D2E),
    scrim = Color.Black.copy(alpha = 0.5f),
    shadow = Color.Black.copy(alpha = 0.25f),
)
