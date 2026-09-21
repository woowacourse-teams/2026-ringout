package com.joon.ringout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThemeModeTest {

    @Test
    fun `저장되지 않은 값은 해결되지 않은 상태다`() {
        assertNull(ThemeMode.fromPersistedValue(null))
    }

    @Test
    fun `유효하지 않은 저장값은 해결되지 않은 상태다`() {
        assertNull(ThemeMode.fromPersistedValue("system"))
    }

    @Test
    fun persistedValuesDecodeToTheirThemeModes() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromPersistedValue("dark"))
        assertEquals(ThemeMode.Light, ThemeMode.fromPersistedValue("light"))
    }

}
