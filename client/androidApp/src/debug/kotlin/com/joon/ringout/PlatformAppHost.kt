package com.joon.ringout

import androidx.compose.runtime.Composable
import com.joon.ringout.presentation.developertools.DeveloperToolsHost
import com.joon.ringout.presentation.developertools.DeveloperToolsRoute

@Composable
internal fun PlatformAppHost(
    content: @Composable (extraContent: (@Composable (ThemeMode) -> Unit)?) -> Unit,
) {
    DeveloperToolsHost(
        toolsContent = { onBack -> DeveloperToolsRoute(currentAppBuildInfo(), onBack) },
        content = { extraContent -> content(extraContent) },
    )
}
