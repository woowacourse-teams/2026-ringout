package com.joon.ringout.presentation.developertools

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal actual fun DeveloperToolsBackHandler(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
}
