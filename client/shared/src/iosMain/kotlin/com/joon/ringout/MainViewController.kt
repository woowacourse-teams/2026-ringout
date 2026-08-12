package com.joon.ringout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.joon.ringout.platform.IosNativeServices
import com.joon.ringout.platform.LocalIosNativeServices
import platform.Foundation.NSBundle

fun MainViewController(nativeServices: IosNativeServices) = ComposeUIViewController {
    val appVersion = NSBundle.mainBundle
        .objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        ?: ""
    CompositionLocalProvider(LocalIosNativeServices provides nativeServices) {
        App(appVersion = appVersion)
    }
}
