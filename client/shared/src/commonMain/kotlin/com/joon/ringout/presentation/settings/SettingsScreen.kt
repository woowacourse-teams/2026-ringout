package com.joon.ringout.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.destination.PlatformBackHandler
import com.joon.ringout.presentation.settings.components.AppInfoCard
import com.joon.ringout.presentation.settings.components.SettingsHeader
import com.joon.ringout.presentation.settings.components.ThemeSettingCard
import com.joon.ringout.presentation.settings.components.settingsColors

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    appVersion: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = settingsColors()

    PlatformBackHandler(onBack = onBackClick)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                start = 20.dp,
                end = 20.dp,
                bottom = 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        SettingsHeader(onBackClick = onBackClick)
        ThemeSettingCard(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )
        AppInfoCard(appVersion = appVersion)
    }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun SettingsScreenDarkPreview() {
    RingoutTheme(themeMode = ThemeMode.Dark) {
        SettingsScreen(
            themeMode = ThemeMode.Dark,
            appVersion = "0.0.2",
            onThemeModeChange = {},
            onBackClick = {},
        )
    }
}

@Preview(widthDp = 402, heightDp = 941)
@Composable
private fun SettingsScreenLightPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        SettingsScreen(
            themeMode = ThemeMode.Light,
            appVersion = "0.0.2",
            onThemeModeChange = {},
            onBackClick = {},
        )
    }
}
