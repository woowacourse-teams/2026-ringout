package com.joon.ringout

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.pretendard_black
import ringout.shared.generated.resources.pretendard_bold
import ringout.shared.generated.resources.pretendard_extra_bold
import ringout.shared.generated.resources.pretendard_light
import ringout.shared.generated.resources.pretendard_medium
import ringout.shared.generated.resources.pretendard_thin

private val RingoutOrange = Color(0xFFFF6D2E)
private val RingoutLightBackground = Color(0xFFF5F5F5)
private val RingoutLightContent = Color(0xFF111827)

internal val LocalRingoutThemeMode = staticCompositionLocalOf { ThemeMode.Dark }

private val RingoutLightColorScheme = lightColorScheme(
    primary = RingoutOrange,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF5C625D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7E0),
    onSecondaryContainer = Color(0xFF191D19),
    background = RingoutLightBackground,
    onBackground = RingoutLightContent,
    surface = Color.White,
    onSurface = RingoutLightContent,
    surfaceVariant = Color(0xFFE1E4DE),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFF767973),
)

private val RingoutDarkColorScheme = darkColorScheme(
    primary = RingoutOrange,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF8A2C00),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFC3C9C3),
    onSecondary = Color(0xFF2D322E),
    secondaryContainer = Color(0xFF434944),
    onSecondaryContainer = Color(0xFFE0E7E0),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2B302C),
    onSurfaceVariant = Color.White,
    outline = Color(0xFF8F938D),
)

@Composable
fun RingoutTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    content: @Composable () -> Unit,
) {
    val pretendard = FontFamily(
        Font(Res.font.pretendard_thin, FontWeight.Thin),
        Font(Res.font.pretendard_light, FontWeight.Light),
        Font(Res.font.pretendard_medium, FontWeight.Normal),
        Font(Res.font.pretendard_medium, FontWeight.Medium),
        Font(Res.font.pretendard_bold, FontWeight.Bold),
        Font(Res.font.pretendard_extra_bold, FontWeight.ExtraBold),
        Font(Res.font.pretendard_black, FontWeight.Black),
    )
    val defaults = Typography()
    val typography = defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = pretendard),
        displayMedium = defaults.displayMedium.copy(fontFamily = pretendard),
        displaySmall = defaults.displaySmall.copy(fontFamily = pretendard),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = pretendard),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = pretendard),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = pretendard),
        titleLarge = defaults.titleLarge.copy(fontFamily = pretendard),
        titleMedium = defaults.titleMedium.copy(fontFamily = pretendard),
        titleSmall = defaults.titleSmall.copy(fontFamily = pretendard),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = pretendard),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = pretendard),
        bodySmall = defaults.bodySmall.copy(fontFamily = pretendard),
        labelLarge = defaults.labelLarge.copy(fontFamily = pretendard),
        labelMedium = defaults.labelMedium.copy(fontFamily = pretendard),
        labelSmall = defaults.labelSmall.copy(fontFamily = pretendard),
    )

    CompositionLocalProvider(LocalRingoutThemeMode provides themeMode) {
        MaterialTheme(
            colorScheme = when (themeMode) {
                ThemeMode.Dark -> RingoutDarkColorScheme
                ThemeMode.Light -> RingoutLightColorScheme
            },
            typography = typography,
            content = content,
        )
    }
}
