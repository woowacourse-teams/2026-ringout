package com.joon.ringout.presentation.destination.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DestinationNicknameValidationTest {
    @Test
    fun blankNicknameIsInvalid() {
        assertNull(normalizeDestinationNickname(""))
        assertNull(normalizeDestinationNickname("   "))
    }

    @Test
    fun nicknameIsTrimmedBeforeSaving() {
        assertEquals(
            "회사",
            normalizeDestinationNickname("  회사  "),
        )
    }

    @Test
    fun nicknameAcceptsOneToTenCharacters() {
        assertEquals("집", normalizeDestinationNickname("집"))
        assertEquals(
            "1234567890",
            normalizeDestinationNickname("1234567890"),
        )
    }

    @Test
    fun nicknameLongerThanTenCharactersIsInvalid() {
        assertNull(normalizeDestinationNickname("12345678901"))
    }

    @Test
    fun inputIsLimitedToTenCharacters() {
        assertEquals(
            "1234567890",
            limitDestinationNicknameInput("12345678901"),
        )
    }
}
