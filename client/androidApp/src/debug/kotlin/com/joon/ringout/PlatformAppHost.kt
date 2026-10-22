package com.joon.ringout

import androidx.compose.runtime.Composable
import com.joon.ringout.di.AppContainer
import com.joon.ringout.presentation.developertools.DeveloperToolsHost
import com.joon.ringout.presentation.developertools.DeveloperToolsRoute

@Composable
internal fun PlatformAppHost(
    appContainer: AppContainer,
    content: @Composable (extraContent: (@Composable (ThemeMode) -> Unit)?) -> Unit,
) {
    DeveloperToolsHost(
        toolsContent = { onBack ->
            DeveloperToolsRoute(
                buildInfo = currentAppBuildInfo(),
                pushTokenRegistrationReadiness = appContainer.pushTokenRegistrationReadiness,
                onBack = onBack,
            )
        },
        content = { extraContent -> content(extraContent) },
    )
}
