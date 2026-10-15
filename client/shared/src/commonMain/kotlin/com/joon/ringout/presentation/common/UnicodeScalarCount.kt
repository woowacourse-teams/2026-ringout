package com.joon.ringout.presentation.common

/** Counts supplementary Unicode characters once and leaves unmatched surrogates as individual units. */
internal fun String.unicodeScalarCount(): Int {
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
