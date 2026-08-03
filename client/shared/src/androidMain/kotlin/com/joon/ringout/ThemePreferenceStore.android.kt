package com.joon.ringout

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val ThemePreferencesName = "ringout_theme_preferences"
private const val ThemeModeKey = "theme_mode"

private class AndroidThemePreferenceStore(
    private val preferences: SharedPreferences,
) : ThemePreferenceStore {
    override fun readThemeMode(): ThemeMode =
        ThemeMode.fromPersistedValue(preferences.getString(ThemeModeKey, null))

    override fun writeThemeMode(themeMode: ThemeMode) {
        preferences.edit()
            .putString(ThemeModeKey, themeMode.persistedValue)
            .apply()
    }
}

@Composable
internal actual fun rememberThemePreferenceStore(): ThemePreferenceStore {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        AndroidThemePreferenceStore(
            preferences = applicationContext.getSharedPreferences(
                ThemePreferencesName,
                Context.MODE_PRIVATE,
            ),
        )
    }
}
