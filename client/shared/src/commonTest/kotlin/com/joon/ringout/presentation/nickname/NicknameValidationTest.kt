package com.joon.ringout.presentation.nickname

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NicknameValidationTest {
    @Test
    fun acceptsKoreanEnglishDigitsAndTheirMixture() {
        assertTrue(validateNickname("가나다라마바사아자차").isValid)
        assertTrue(validateNickname("RingOut").isValid)
        assertTrue(validateNickname("20260812").isValid)
        assertTrue(validateNickname("링out2026").isValid)
    }

    @Test
    fun emptyNicknameIsInvalid() {
        val result = validateNickname("")

        assertEquals(0, result.characterCount)
        assertFalse(result.isLengthValid)
        assertFalse(result.hasOnlyAllowedCharacters)
        assertFalse(result.isValid)
    }

    @Test
    fun elevenCharacterNicknameFailsOnlyTheLengthRule() {
        val result = validateNickname("12345678901")

        assertEquals(11, result.characterCount)
        assertFalse(result.isLengthValid)
        assertTrue(result.hasOnlyAllowedCharacters)
        assertFalse(result.isValid)
    }

    @Test
    fun symbolFailsTheAllowedCharacterRule() {
        val result = validateNickname("링아웃!")

        assertTrue(result.isLengthValid)
        assertFalse(result.hasOnlyAllowedCharacters)
        assertFalse(result.isValid)
    }

    @Test
    fun whitespaceIsNotTrimmedAndIsInvalid() {
        assertFalse(validateNickname("링 아웃").isValid)
        assertFalse(validateNickname(" RingOut").isValid)
        assertFalse(validateNickname("RingOut\n").isValid)
    }

    @Test
    fun isolatedHangulJamoIsInvalid() {
        assertFalse(validateNickname("ㄱ").isValid)
        assertFalse(validateNickname("\u1100").isValid)
    }

    @Test
    fun supplementaryUnicodeCharacterCountsAsOneButIsNotAllowed() {
        val result = validateNickname("😀")

        assertEquals(1, result.characterCount)
        assertTrue(result.isLengthValid)
        assertFalse(result.hasOnlyAllowedCharacters)
        assertFalse(result.isValid)
    }
}
