package com.joon.ringout.presentation.destination.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DestinationNicknameValidationTest {
    @Test
    fun `빈 별명은 저장할 수 없다`() {
        assertNull(normalizeDestinationNickname(""))
        assertNull(normalizeDestinationNickname("   "))
    }

    @Test
    fun `저장 전 별명 앞뒤 공백을 제거한다`() {
        assertEquals(
            "회사",
            normalizeDestinationNickname("  회사  "),
        )
    }

    @Test
    fun `한 자에서 12자까지 목적지 별명을 저장할 수 있다`() {
        assertEquals("집", normalizeDestinationNickname("집"))
        assertEquals(
            "123456789012",
            normalizeDestinationNickname("123456789012"),
        )
    }

    @Test
    fun `12자를 넘은 목적지 별명은 저장할 수 없다`() {
        assertNull(normalizeDestinationNickname("1234567890123"))
    }

    @Test
    fun `현재 별명에 12번째 글자까지 입력할 수 있다`() {
        assertEquals(
            "123456789012",
            applyDestinationNicknameInput("12345678901", "123456789012"),
        )
    }

    @Test
    fun `13자째 입력과 긴 붙여넣기는 일부만 반영하지 않는다`() {
        val atLimit = "123456789012"

        assertEquals(atLimit, applyDestinationNicknameInput(atLimit, atLimit + "3"))
        assertEquals("", applyDestinationNicknameInput("", "1234567890123"))
    }

    @Test
    fun `12자를 넘은 기존 별명은 한 글자씩 줄일 수 있다`() {
        assertEquals(
            "123456789012",
            applyDestinationNicknameInput("1234567890123", "123456789012"),
        )
    }
}
