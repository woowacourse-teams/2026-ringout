package com.joon.ringout.presentation.nickname

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
    val characterCount = value.unicodeCharacterCount()
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

/** Counts Unicode scalar values rather than UTF-16 code units. */
private fun String.unicodeCharacterCount(): Int {
    var count = 0
    var index = 0

    while (index < length) {
        val current = this[index]
        val hasSurrogatePair =
            current in '\uD800'..'\uDBFF' &&
                index + 1 < length &&
                this[index + 1] in '\uDC00'..'\uDFFF'

        index += if (hasSurrogatePair) 2 else 1
        count += 1
    }

    return count
}
