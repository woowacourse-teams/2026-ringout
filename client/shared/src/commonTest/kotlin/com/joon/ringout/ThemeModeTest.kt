package com.joon.ringout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThemeModeTest {

    @Test
    fun missingPersistedValueIsUnresolved() {
        assertNull(ThemeMode.fromPersistedValue(null))
    }

    @Test
    fun invalidPersistedValueIsUnresolved() {
        assertNull(ThemeMode.fromPersistedValue("system"))
    }

    @Test
    fun persistedValuesDecodeToTheirThemeModes() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromPersistedValue("dark"))
        assertEquals(ThemeMode.Light, ThemeMode.fromPersistedValue("light"))
    }

}
