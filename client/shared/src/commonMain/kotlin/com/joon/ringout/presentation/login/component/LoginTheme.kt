package com.joon.ringout.presentation.login.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode

@Immutable
internal data class LoginColors(
    val background: Color,
    val primaryText: Color,
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
    val headerContainerHeight: Dp,
    val headerVisualHeight: Dp,
    val headerToImageSpacing: Dp,
    val heroImageSize: Dp,
    val imageToTextSpacing: Dp,
    val heroTextHeight: Dp,
    val textToSocialSpacing: Dp,
    val socialPadding: Dp,
    val socialButtonSpacing: Dp,
    val socialButtonWidth: Dp,
    val socialButtonHeight: Dp,
) {
    val headerContainerToImageSpacing: Dp
        get() = headerToImageSpacing - (headerContainerHeight - headerVisualHeight) / 2f
}

private val LoginDarkColors = LoginColors(
    background = Color(0xFF0F1012),
    primaryText = Color(0xFFF5F5F6),
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
    googleBackground = Color(0xFFFFFFFF),
    googleBorder = Color(0xFFD1D5DB),
    googleText = Color(0xFF374151),
    kakaoBackground = Color(0xFFFEE500),
    kakaoText = Color(0xFF3C1E1E),
    naverBackground = Color(0xFF03A94D),
    naverText = Color(0xFFFFFFFF),
)

private val LoginDarkDimensions = LoginDimensions(
    headerContainerHeight = 40.dp,
    headerVisualHeight = 24.dp,
    headerToImageSpacing = 20.dp,
    heroImageSize = 229.dp,
    imageToTextSpacing = 20.dp,
    heroTextHeight = 76.dp,
    textToSocialSpacing = 80.dp,
    socialPadding = 10.dp,
    socialButtonSpacing = 10.dp,
    socialButtonWidth = 308.dp,
    socialButtonHeight = 56.dp,
)

private val LoginLightDimensions = LoginDimensions(
    headerContainerHeight = 40.dp,
    headerVisualHeight = 24.dp,
    headerToImageSpacing = 50.dp,
    heroImageSize = 263.dp,
    imageToTextSpacing = 10.dp,
    heroTextHeight = 76.dp,
    textToSocialSpacing = 60.dp,
    socialPadding = 10.dp,
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
