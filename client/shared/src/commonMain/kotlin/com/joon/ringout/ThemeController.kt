package com.joon.ringout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

enum class ThemeMode(
    internal val persistedValue: String,
) {
    Dark("dark"),
    Light("light"),
    ;

    internal companion object {
        fun fromPersistedValue(value: String?): ThemeMode =
            entries.firstOrNull { it.persistedValue == value } ?: Dark
    }
}

internal interface ThemePreferenceStore {
    fun readThemeMode(): ThemeMode

    fun writeThemeMode(themeMode: ThemeMode)
}

@Composable
internal expect fun rememberThemePreferenceStore(): ThemePreferenceStore

@Stable
class ThemeController internal constructor(
    initialThemeMode: ThemeMode,
    private val preferenceStore: ThemePreferenceStore,
) {
    private var currentThemeMode by mutableStateOf(initialThemeMode)

    val themeMode: ThemeMode
        get() = currentThemeMode

    fun setThemeMode(themeMode: ThemeMode) {
        if (currentThemeMode == themeMode) return

        currentThemeMode = themeMode
        preferenceStore.writeThemeMode(themeMode)
    }
}

@Composable
fun rememberThemeController(): ThemeController {
    val preferenceStore = rememberThemePreferenceStore()
    return remember(preferenceStore) {
        ThemeController(
            initialThemeMode = preferenceStore.readThemeMode(),
            preferenceStore = preferenceStore,
        )
    }
}
