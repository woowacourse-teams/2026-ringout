package com.joon.ringout.domain.preferences

import com.joon.ringout.ThemeMode

fun interface SystemThemeModeReader {
    fun read(): ThemeMode
}
