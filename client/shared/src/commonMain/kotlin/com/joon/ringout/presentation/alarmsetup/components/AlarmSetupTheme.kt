package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import org.jetbrains.compose.resources.DrawableResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.alarm_setup_back_dark
import ringout.shared.generated.resources.alarm_setup_back_light
import ringout.shared.generated.resources.alarm_setup_chevron_dark
import ringout.shared.generated.resources.alarm_setup_chevron_light
import ringout.shared.generated.resources.alarm_setup_thumb_dark
import ringout.shared.generated.resources.alarm_setup_thumb_light

internal data class AlarmSetupColors(
    val accent: Color,
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val supportingText: Color,
    val timeCard: Color,
    val selectedContent: Color,
    val unselectedDayBackground: Color,
    val unselectedDayText: Color,
    val inactiveTrack: Color,
    val backIcon: DrawableResource,
    val chevronIcon: DrawableResource,
    val sliderThumb: DrawableResource,
)

@Composable
internal fun alarmSetupColors(): AlarmSetupColors {
    val colorScheme = MaterialTheme.colorScheme

    return if (LocalRingoutThemeMode.current == ThemeMode.Dark) {
        AlarmSetupColors(
            accent = colorScheme.primary,
            background = colorScheme.background,
            primaryText = AlarmSetupDarkPalette.textPrimary,
            secondaryText = AlarmSetupDarkPalette.textSecondary,
            supportingText = AlarmSetupDarkPalette.textMuted,
            timeCard = colorScheme.surface,
            selectedContent = AlarmSetupDarkPalette.selectedContent,
            unselectedDayBackground = AlarmSetupDarkPalette.textMuted,
            unselectedDayText = AlarmSetupDarkPalette.selectedContent,
            inactiveTrack = AlarmSetupDarkPalette.textMuted,
            backIcon = Res.drawable.alarm_setup_back_dark,
            chevronIcon = Res.drawable.alarm_setup_chevron_dark,
            sliderThumb = Res.drawable.alarm_setup_thumb_dark,
        )
    } else {
        AlarmSetupColors(
            accent = colorScheme.primary,
            background = colorScheme.surface,
            primaryText = colorScheme.onSurface,
            secondaryText = colorScheme.onSurfaceVariant,
            supportingText = colorScheme.onSurfaceVariant,
            timeCard = colorScheme.background,
            selectedContent = AlarmSetupLightPalette.selectedContent,
            unselectedDayBackground = AlarmSetupLightPalette.unselectedDay,
            unselectedDayText = colorScheme.onSurface,
            inactiveTrack = AlarmSetupLightPalette.inactiveTrack,
            backIcon = Res.drawable.alarm_setup_back_light,
            chevronIcon = Res.drawable.alarm_setup_chevron_light,
            sliderThumb = Res.drawable.alarm_setup_thumb_light,
        )
    }
}

private object AlarmSetupDarkPalette {
    val textPrimary = Color(0xFFF5F5F6)
    val textSecondary = Color(0xFFA7A9B0)
    val textMuted = Color(0xFF8C8C8C)
    val selectedContent = Color.White
}

private object AlarmSetupLightPalette {
    val selectedContent = Color.White
    val unselectedDay = Color(0xFFE5E7EB)
    val inactiveTrack = Color(0xFFE5E7EB)
}
