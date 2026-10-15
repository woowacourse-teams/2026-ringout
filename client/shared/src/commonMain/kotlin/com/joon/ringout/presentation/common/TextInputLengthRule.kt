package com.joon.ringout.presentation.common

internal class TextInputLengthRule(
    val maxLength: Int = DefaultMaxLength,
) {
    init {
        require(maxLength > 0) { "maxLength must be greater than zero" }
    }

    fun allows(current: String, proposed: String): Boolean {
        val currentLength = current.unicodeScalarCount()
        val proposedLength = proposed.unicodeScalarCount()

        return if (currentLength > maxLength) {
            proposedLength < currentLength
        } else {
            proposedLength <= maxLength
        }
    }

    private companion object {
        const val DefaultMaxLength = 300
    }
}
