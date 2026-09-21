package com.joon.ringout.data.preferences

import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.preferences.SystemThemeModeReader
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceStyle

class IosSystemThemeModeReader : SystemThemeModeReader {
    override fun read(): ThemeMode = when (UIScreen.mainScreen.traitCollection.userInterfaceStyle) {
        UIUserInterfaceStyle.UIUserInterfaceStyleLight -> ThemeMode.Light
        UIUserInterfaceStyle.UIUserInterfaceStyleDark -> ThemeMode.Dark
        else -> ThemeMode.Dark
    }
}
