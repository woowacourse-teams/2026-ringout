package com.joon.ringout

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSBundle

fun MainViewController() = ComposeUIViewController {
    val appVersion = NSBundle.mainBundle
        .objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        ?: ""
    App(appVersion = appVersion)
}
