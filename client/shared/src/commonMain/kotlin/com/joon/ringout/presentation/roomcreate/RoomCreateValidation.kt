package com.joon.ringout.presentation.roomcreate

internal const val RoomNameMinLength = 2
internal const val RoomNameMaxLength = 20
internal const val RoomIntroductionMaxLength = 300

internal data class RoomNameValidation(
    val normalizedValue: String,
    val characterCount: Int,
    val isLengthValid: Boolean,
    val hasOnlyAllowedCharacters: Boolean,
) {
    val isValid: Boolean
        get() = isLengthValid && hasOnlyAllowedCharacters
}

internal fun validateRoomName(rawValue: String): RoomNameValidation {
    val normalizedValue = rawValue.trim()
    val characterCount = normalizedValue.unicodeScalarCount()

    return RoomNameValidation(
        normalizedValue = normalizedValue,
        characterCount = characterCount,
        isLengthValid = characterCount in RoomNameMinLength..RoomNameMaxLength,
        hasOnlyAllowedCharacters =
            normalizedValue.isNotEmpty() && normalizedValue.all(Char::isAllowedRoomNameCharacter),
    )
}

internal data class RoomIntroductionValidation(
    val characterCount: Int,
    val isNotBlank: Boolean,
    val isLengthValid: Boolean,
) {
    val isValid: Boolean
        get() = isNotBlank && isLengthValid
}

internal fun validateRoomIntroduction(value: String): RoomIntroductionValidation {
    val characterCount = value.unicodeScalarCount()

    return RoomIntroductionValidation(
        characterCount = characterCount,
        isNotBlank = value.isNotBlank(),
        isLengthValid = characterCount in 1..RoomIntroductionMaxLength,
    )
}

private fun Char.isAllowedRoomNameCharacter(): Boolean =
    this in '\uAC00'..'\uD7A3' ||
        this in 'A'..'Z' ||
        this in 'a'..'z' ||
        this in '0'..'9'

/** Counts Unicode scalar values so supplementary characters count once. */
private fun String.unicodeScalarCount(): Int {
    var count = 0
    var index = 0

    while (index < length) {
        val isSurrogatePair =
            this[index] in '\uD800'..'\uDBFF' &&
                index + 1 < length &&
                this[index + 1] in '\uDC00'..'\uDFFF'

        index += if (isSurrogatePair) 2 else 1
        count += 1
    }

    return count
}
