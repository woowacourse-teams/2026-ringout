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

}
