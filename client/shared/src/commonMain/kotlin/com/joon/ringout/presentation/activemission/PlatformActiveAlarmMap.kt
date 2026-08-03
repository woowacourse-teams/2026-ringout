package com.joon.ringout.presentation.activemission

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PlatformActiveAlarmMap(
    destinationLatitude: Double,
    destinationLongitude: Double,
    currentLatitude: Double?,
    currentLongitude: Double?,
    onMapError: (String) -> Unit,
    modifier: Modifier = Modifier,
)
