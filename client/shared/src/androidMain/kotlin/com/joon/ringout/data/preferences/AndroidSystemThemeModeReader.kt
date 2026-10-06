package com.joon.ringout.data.preferences

import android.content.Context
import android.content.res.Configuration
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.preferences.SystemThemeModeReader

class AndroidSystemThemeModeReader(
    context: Context,
) : SystemThemeModeReader {
    private val resources = context.applicationContext.resources

    override fun read(): ThemeMode = when (
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    ) {
        Configuration.UI_MODE_NIGHT_NO -> ThemeMode.Light
        Configuration.UI_MODE_NIGHT_YES -> ThemeMode.Dark
        else -> ThemeMode.Dark
    }
}
