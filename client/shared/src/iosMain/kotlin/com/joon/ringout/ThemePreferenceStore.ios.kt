package com.joon.ringout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

private const val ThemeModeKey = "theme_mode"

private class IosThemePreferenceStore(
    private val userDefaults: NSUserDefaults,
) : ThemePreferenceStore {
    override fun readThemeMode(): ThemeMode =
        ThemeMode.fromPersistedValue(userDefaults.stringForKey(ThemeModeKey))

    override fun writeThemeMode(themeMode: ThemeMode) {
        userDefaults.setObject(themeMode.persistedValue, forKey = ThemeModeKey)
    }
}

@Composable
internal actual fun rememberThemePreferenceStore(): ThemePreferenceStore =
    remember {
        IosThemePreferenceStore(NSUserDefaults.standardUserDefaults)
    }
