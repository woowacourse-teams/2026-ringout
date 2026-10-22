package com.joon.ringout

import com.joon.ringout.platform.IosNativeServices
import com.joon.ringout.presentation.developertools.DeveloperToolsHost
import com.joon.ringout.presentation.developertools.DeveloperToolsRoute
import platform.UIKit.UIViewController

fun DebugMainViewController(nativeServices: IosNativeServices): UIViewController =
    MainViewControllerWithContent(nativeServices) { appContainer, content ->
        DeveloperToolsHost(
            toolsContent = { onBack ->
                DeveloperToolsRoute(
                    buildInfo = currentIosAppBuildInfo(),
                    pushTokenRegistrationReadiness = appContainer.pushTokenRegistrationReadiness,
                    onBack = onBack,
                )
            },
            content = { extraContent -> content(extraContent) },
        )
    }
