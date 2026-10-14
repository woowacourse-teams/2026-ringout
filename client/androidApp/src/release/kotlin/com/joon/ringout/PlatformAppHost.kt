package com.joon.ringout

import androidx.compose.runtime.Composable

@Composable
internal fun PlatformAppHost(
    content: @Composable (extraContent: (@Composable (ThemeMode) -> Unit)?) -> Unit,
) {
    content(null)
}
