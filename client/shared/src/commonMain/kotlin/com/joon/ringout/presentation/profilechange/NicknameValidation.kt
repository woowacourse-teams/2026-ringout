package com.joon.ringout.presentation.profilechange

import com.joon.ringout.presentation.common.unicodeScalarCount

internal const val NicknameMinLength = 1
internal const val NicknameMaxLength = 10

/**
 * Result of validating a nickname exactly as entered.
 *
 * Whitespace is not trimmed because it is outside the allowed character set.
 * Only precomposed Hangul syllables (`가`-`힣`), ASCII English letters, and
 * ASCII digits are accepted. Consequently, an empty value and isolated Hangul
 * jamo are invalid.
 */
internal data class NicknameValidation(
    val characterCount: Int,
    val isLengthValid: Boolean,
    val hasOnlyAllowedCharacters: Boolean,
) {
    val isValid: Boolean
        get() = isLengthValid && hasOnlyAllowedCharacters
}

internal fun validateNickname(value: String): NicknameValidation {
    val characterCount = value.unicodeScalarCount()
    val isLengthValid = characterCount in NicknameMinLength..NicknameMaxLength
    val hasOnlyAllowedCharacters =
        value.isNotEmpty() && value.all(Char::isAllowedNicknameCharacter)

    return NicknameValidation(
        characterCount = characterCount,
        isLengthValid = isLengthValid,
        hasOnlyAllowedCharacters = hasOnlyAllowedCharacters,
    )
}

private fun Char.isAllowedNicknameCharacter(): Boolean =
    this in '\uAC00'..'\uD7A3' ||
        this in 'A'..'Z' ||
        this in 'a'..'z' ||
        this in '0'..'9'
