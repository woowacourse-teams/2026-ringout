package com.joon.ringout

import com.joon.ringout.platform.IosNativeServices
import com.joon.ringout.presentation.developertools.DeveloperToolsHost
import com.joon.ringout.presentation.developertools.component.DeveloperToolsPendingContent
import platform.UIKit.UIViewController

fun DebugMainViewController(nativeServices: IosNativeServices): UIViewController =
    MainViewControllerWithContent(nativeServices) { content ->
        DeveloperToolsHost(
            toolsContent = { onBack -> DeveloperToolsPendingContent(onBack = onBack) },
            content = { extraContent -> content(extraContent) },
        )
    }
