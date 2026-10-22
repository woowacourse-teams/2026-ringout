package com.joon.ringout

import androidx.compose.runtime.Composable
import com.joon.ringout.di.AppContainer

@Composable
internal fun PlatformAppHost(
    appContainer: AppContainer,
    content: @Composable (extraContent: (@Composable (ThemeMode) -> Unit)?) -> Unit,
) {
    content(null)
}
