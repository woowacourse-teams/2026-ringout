package com.joon.ringout

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeModeTest {

    @Test
    fun missingPersistedValueDefaultsToDark() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromPersistedValue(null))
    }

    @Test
    fun invalidPersistedValueDefaultsToDark() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromPersistedValue("system"))
    }

    @Test
    fun persistedValuesDecodeToTheirThemeModes() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromPersistedValue("dark"))
        assertEquals(ThemeMode.Light, ThemeMode.fromPersistedValue("light"))
    }

    @Test
    fun controllerPersistsThemeChanges() {
        val preferenceStore = FakeThemePreferenceStore(ThemeMode.Dark)
        val controller = ThemeController(
            initialThemeMode = preferenceStore.readThemeMode(),
            preferenceStore = preferenceStore,
        )

        controller.setThemeMode(ThemeMode.Light)

        assertEquals(ThemeMode.Light, controller.themeMode)
        assertEquals(ThemeMode.Light, preferenceStore.persistedThemeMode)
        assertEquals(1, preferenceStore.writeCount)
    }

    @Test
    fun controllerDoesNotPersistUnchangedTheme() {
        val preferenceStore = FakeThemePreferenceStore(ThemeMode.Dark)
        val controller = ThemeController(
            initialThemeMode = preferenceStore.readThemeMode(),
            preferenceStore = preferenceStore,
        )

        controller.setThemeMode(ThemeMode.Dark)

        assertEquals(0, preferenceStore.writeCount)
    }
}

private class FakeThemePreferenceStore(
    initialThemeMode: ThemeMode,
) : ThemePreferenceStore {
    var persistedThemeMode: ThemeMode = initialThemeMode
        private set

    var writeCount: Int = 0
        private set

    override fun readThemeMode(): ThemeMode = persistedThemeMode

    override fun writeThemeMode(themeMode: ThemeMode) {
        persistedThemeMode = themeMode
        writeCount += 1
    }
}
