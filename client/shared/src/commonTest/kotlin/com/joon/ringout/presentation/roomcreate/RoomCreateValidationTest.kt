package com.joon.ringout.presentation.roomcreate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomCreateValidationTest {
    @Test
    fun `모임 이름은 앞뒤 공백을 제거한 뒤 한글 영문 숫자와 길이를 검증한다`() {
        val result = validateRoomName(" 가A2 ")

        assertEquals("가A2", result.normalizedValue)
        assertEquals(3, result.characterCount)
        assertTrue(result.isLengthValid)
        assertTrue(result.hasOnlyAllowedCharacters)
        assertTrue(result.isValid)
    }

    @Test
    fun `모임 이름 길이와 허용 문자 조건은 각각 검증한다`() {
        val tooShort = validateRoomName("가")
        assertFalse(tooShort.isLengthValid)
        assertTrue(tooShort.hasOnlyAllowedCharacters)

        val internalWhitespace = validateRoomName("가 나")
        assertTrue(internalWhitespace.isLengthValid)
        assertFalse(internalWhitespace.hasOnlyAllowedCharacters)

        val specialCharacter = validateRoomName("가A!")
        assertTrue(specialCharacter.isLengthValid)
        assertFalse(specialCharacter.hasOnlyAllowedCharacters)

        val tooLong = validateRoomName("가".repeat(RoomNameMaxLength + 1))
        assertFalse(tooLong.isLengthValid)
        assertTrue(tooLong.hasOnlyAllowedCharacters)
    }

    @Test
    fun `공백뿐인 이름은 정규화 후 빈 값으로 검증한다`() {
        val result = validateRoomName("   \n")

        assertEquals("", result.normalizedValue)
        assertFalse(result.isLengthValid)
        assertFalse(result.hasOnlyAllowedCharacters)
        assertFalse(result.isValid)
    }

    @Test
    fun `모임 소개는 공백 외 내용과 1자에서 300자까지 허용한다`() {
        assertTrue(validateRoomIntroduction(" 소개 ").isValid)
        assertTrue(validateRoomIntroduction("가".repeat(RoomIntroductionMaxLength)).isValid)
    }

    @Test
    fun `공백뿐인 소개와 301자는 유효하지 않다`() {
        val blank = validateRoomIntroduction(" \n ")
        assertFalse(blank.isNotBlank)
        assertFalse(blank.isValid)

        val overLimit = validateRoomIntroduction("가".repeat(RoomIntroductionMaxLength + 1))
        assertEquals(RoomIntroductionMaxLength + 1, overLimit.characterCount)
        assertFalse(overLimit.isLengthValid)
        assertFalse(overLimit.isValid)
    }

    @Test
    fun `소개 글자 수는 서로게이트 쌍을 하나로 센다`() {
        val exactlyThreeHundred = "가".repeat(RoomIntroductionMaxLength - 1) + "😀"
        val threeHundredAndOne = "가".repeat(RoomIntroductionMaxLength) + "😀"

        assertEquals(RoomIntroductionMaxLength, validateRoomIntroduction(exactlyThreeHundred).characterCount)
        assertTrue(validateRoomIntroduction(exactlyThreeHundred).isValid)
        assertEquals(RoomIntroductionMaxLength + 1, validateRoomIntroduction(threeHundredAndOne).characterCount)
        assertFalse(validateRoomIntroduction(threeHundredAndOne).isLengthValid)
    }
}
