package com.joon.ringout.presentation.developertools

import androidx.compose.runtime.Composable

// iOS uses the visible back button; there is no Android-style system back action.
@Composable
internal actual fun DeveloperToolsBackHandler(onBack: () -> Unit) = Unit
