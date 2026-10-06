package com.joon.ringout.presentation.roomedit.model

internal data class RoomEditNameValidation(
    val value: String,
) {
    val normalizedValue: String = value.trim()
    val isLengthValid: Boolean = normalizedValue.length in 2..20
    val hasOnlyAllowedCharacters: Boolean =
        normalizedValue.isNotEmpty() && normalizedValue.all { character ->
            character == ' ' ||
                character in '0'..'9' ||
                character in 'A'..'Z' ||
                character in 'a'..'z' ||
                character in '가'..'힣'
        }
    val isValid: Boolean = isLengthValid && hasOnlyAllowedCharacters
}

internal data class RoomEditDescriptionValidation(
    val value: String,
) {
    val isLengthValid: Boolean = value.length <= 300
    val isValid: Boolean = isLengthValid
}

internal fun validateRoomEditName(value: String): RoomEditNameValidation = RoomEditNameValidation(value)

internal fun validateRoomEditDescription(value: String): RoomEditDescriptionValidation =
    RoomEditDescriptionValidation(value)
