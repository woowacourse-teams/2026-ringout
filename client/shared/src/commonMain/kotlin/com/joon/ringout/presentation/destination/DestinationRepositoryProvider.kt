package com.joon.ringout.presentation.destination

import androidx.compose.runtime.Composable
import com.joon.ringout.domain.destination.DestinationRepository

@Composable
internal expect fun rememberDestinationRepository(): DestinationRepository
