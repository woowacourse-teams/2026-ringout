package com.joon.ringout

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
