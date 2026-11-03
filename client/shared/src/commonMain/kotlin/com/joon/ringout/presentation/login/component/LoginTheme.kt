package com.joon.ringout.presentation.login.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode

@Immutable
internal data class LoginColors(
    val background: Color,
    val primaryText: Color,
    val accent: Color,
    val loadingScrim: Color,
    val loadingIndicator: Color,
    val appleBackground: Color,
    val appleBorder: Color,
    val appleText: Color,
    val googleBackground: Color,
    val googleBorder: Color,
    val googleText: Color,
    val kakaoBackground: Color,
    val kakaoText: Color,
    val naverBackground: Color,
    val naverText: Color,
)

@Immutable
internal data class LoginDimensions(
    val referenceScreenHeight: Dp,
    val referenceLogoTop: Dp,
    val referenceFirstButtonTop: Dp,
    val contentHorizontalPadding: Dp,
    val heroLogoSize: Dp,
    val logoToGreetingSpacing: Dp,
    val greetingToDescriptionSpacing: Dp,
    val greetingFontSize: TextUnit,
    val greetingLineHeight: TextUnit,
    val descriptionFontSize: TextUnit,
    val descriptionLineHeight: TextUnit,
    val socialPadding: Dp,
    val socialButtonSpacing: Dp,
    val socialButtonWidth: Dp,
    val socialButtonHeight: Dp,
)

private val LoginDarkColors = LoginColors(
    background = Color(0xFF0F1012),
    primaryText = Color.White,
    accent = Color(0xFFF95E25),
    loadingScrim = Color.Black.copy(alpha = 0.5f),
    loadingIndicator = Color(0xFFFF6D2E),
    appleBackground = Color(0xFFFFFFFF),
    appleBorder = Color.Black,
    appleText = Color(0xFF374151),
    googleBackground = Color(0xFFFFFFFF),
    googleBorder = Color(0xFFE5E7EB),
    googleText = Color(0xFF374151),
    kakaoBackground = Color(0xFFFEE500),
    kakaoText = Color(0xFF3C1E1E),
    naverBackground = Color(0xFF03A94D),
    naverText = Color(0xFFFFFFFF),
)

private val LoginLightColors = LoginColors(
    background = Color(0xFFFFFFFF),
    primaryText = Color(0xFF111827),
    accent = Color(0xFFF95E25),
    loadingScrim = Color.Black.copy(alpha = 0.5f),
    loadingIndicator = Color(0xFFFF6D2E),
    appleBackground = Color(0xFFFFFFFF),
    appleBorder = Color(0xFFD1D5DB),
    appleText = Color(0xFF374151),
    googleBackground = Color(0xFFFFFFFF),
    googleBorder = Color(0xFFD1D5DB),
    googleText = Color(0xFF374151),
    kakaoBackground = Color(0xFFFEE500),
    kakaoText = Color(0xFF3C1E1E),
    naverBackground = Color(0xFF03A94D),
    naverText = Color(0xFFFFFFFF),
)

private val LoginDarkDimensions = LoginDimensions(
    referenceScreenHeight = 941.dp,
    referenceLogoTop = 114.dp,
    referenceFirstButtonTop = 478.dp,
    contentHorizontalPadding = 40.dp,
    heroLogoSize = 67.dp,
    logoToGreetingSpacing = 14.dp,
    greetingToDescriptionSpacing = 24.dp,
    greetingFontSize = 36.sp,
    greetingLineHeight = 43.sp,
    descriptionFontSize = 20.sp,
    descriptionLineHeight = 32.sp,
    socialPadding = 7.dp,
    socialButtonSpacing = 10.dp,
    socialButtonWidth = 308.dp,
    socialButtonHeight = 56.dp,
)

private val LoginLightDimensions = LoginDimensions(
    referenceScreenHeight = 941.dp,
    referenceLogoTop = 114.dp,
    referenceFirstButtonTop = 478.dp,
    contentHorizontalPadding = 40.dp,
    heroLogoSize = 67.dp,
    logoToGreetingSpacing = 14.dp,
    greetingToDescriptionSpacing = 24.dp,
    greetingFontSize = 36.sp,
    greetingLineHeight = 43.sp,
    descriptionFontSize = 20.sp,
    descriptionLineHeight = 32.sp,
    socialPadding = 7.dp,
    socialButtonSpacing = 10.dp,
    socialButtonWidth = 308.dp,
    socialButtonHeight = 56.dp,
)

@Composable
internal fun loginColors(): LoginColors = when (LocalRingoutThemeMode.current) {
    ThemeMode.Dark -> LoginDarkColors
    ThemeMode.Light -> LoginLightColors
}

@Composable
internal fun loginDimensions(): LoginDimensions = when (LocalRingoutThemeMode.current) {
    ThemeMode.Dark -> LoginDarkDimensions
    ThemeMode.Light -> LoginLightDimensions
}
