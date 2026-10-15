package com.joon.ringout.presentation.common

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextInputLengthRuleTest {
    @Test
    fun `기본 제한은 300자다`() {
        val rule = TextInputLengthRule()

        assertTrue(rule.allows("", "가".repeat(300)))
        assertFalse(rule.allows("", "가".repeat(301)))
    }

    @Test
    fun `필드별 제한을 지정할 수 있다`() {
        listOf(10, 20, 12).forEach { maxLength ->
            val rule = TextInputLengthRule(maxLength)

            assertTrue(rule.allows("", "가".repeat(maxLength)))
            assertFalse(rule.allows("", "가".repeat(maxLength + 1)))
        }
    }

    @Test
    fun `한 글자 초과와 초과 붙여넣기는 전체 변경을 거부한다`() {
        val rule = TextInputLengthRule(maxLength = 10)
        val current = "가".repeat(10)

        assertFalse(rule.allows(current, current + "나"))
        assertFalse(rule.allows("", "가".repeat(11)))
    }

    @Test
    fun `한도에 도달해도 삭제와 같은 길이 교체를 허용한다`() {
        val rule = TextInputLengthRule(maxLength = 10)

        assertTrue(rule.allows("가".repeat(10), "가".repeat(9)))
        assertTrue(rule.allows("가".repeat(10), "가".repeat(9) + "나"))
    }

    @Test
    fun `한도를 넘은 기존 값은 더 짧아지는 수정만 허용한다`() {
        val rule = TextInputLengthRule(maxLength = 10)
        val current = "가".repeat(12)

        assertTrue(rule.allows(current, "가".repeat(11)))
        assertFalse(rule.allows(current, current))
        assertFalse(rule.allows(current, "가".repeat(11) + "나"))
    }

    @Test
    fun `서로게이트 쌍은 한 글자로 세고 쌍을 나누지 않는다`() {
        val rule = TextInputLengthRule(maxLength = 1)

        assertTrue(rule.allows("", "😀"))
        assertFalse(rule.allows("😀", "😀😀"))
    }

    @Test
    fun `0 이하의 제한은 생성할 수 없다`() {
        assertFailsWith<IllegalArgumentException> { TextInputLengthRule(0) }
        assertFailsWith<IllegalArgumentException> { TextInputLengthRule(-1) }
    }
}
