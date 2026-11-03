package com.joon.ringout.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.login.component.LoginHero
import com.joon.ringout.presentation.login.component.LoginLoadingOverlay
import com.joon.ringout.presentation.login.component.LoginStatus
import com.joon.ringout.presentation.login.component.SocialLoginButtons
import com.joon.ringout.presentation.login.component.loginColors
import com.joon.ringout.presentation.login.component.loginDimensions

enum class SocialLoginProvider {
    Apple,
    Google,
    Kakao,
}

private const val MaximumSocialLoginProviderCount = 3

@Composable
internal fun LoginScreen(
    uiState: LoginUiState,
    onSocialLoginClick: (SocialLoginProvider) -> Unit,
    modifier: Modifier = Modifier,
    providers: List<SocialLoginProvider> = PlatformSocialLoginProviders,
) {
    val colors = loginColors()
    val dimensions = loginDimensions()
    val shouldShowLoadingOverlay = uiState.shouldShowLoadingOverlay
    val backgroundSemantics = if (shouldShowLoadingOverlay) {
        Modifier.clearAndSetSemantics {}
    } else {
        Modifier
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
        val safeTopInset = safeDrawingPadding.calculateTopPadding()
        val safeBottomInset = safeDrawingPadding.calculateBottomPadding()
        val verticalScale = (maxHeight / dimensions.referenceScreenHeight).coerceAtMost(1f)
        val logoTop = dimensions.referenceLogoTop * verticalScale
        val firstButtonTop = dimensions.referenceFirstButtonTop * verticalScale
        val logoTopSpacing = (logoTop - safeTopInset).coerceAtLeast(0.dp)
        val estimatedHeroHeight = with(LocalDensity.current) {
            dimensions.heroLogoSize +
                dimensions.logoToGreetingSpacing +
                dimensions.greetingLineHeight.toDp() +
                dimensions.greetingToDescriptionSpacing +
                dimensions.descriptionLineHeight.toDp() * 2
        }
        val reservedButtonAreaHeight = dimensions.socialButtonHeight * MaximumSocialLoginProviderCount +
            dimensions.socialButtonSpacing * (MaximumSocialLoginProviderCount - 1)
        val errorAreaHeight = if (uiState.errorMessage == null) 0.dp else 48.dp
        val availableDecorativeSpacing = (
            maxHeight - safeTopInset - safeBottomInset - logoTopSpacing - estimatedHeroHeight -
                reservedButtonAreaHeight - errorAreaHeight - 12.dp
            ).coerceAtLeast(0.dp)
        val referenceDecorativeSpacing = (firstButtonTop - logoTop - estimatedHeroHeight).coerceAtLeast(0.dp)
        val heroToButtonsSpacing = minOf(referenceDecorativeSpacing, availableDecorativeSpacing)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensions.contentHorizontalPadding)
                .then(backgroundSemantics),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(logoTopSpacing))
            LoginHero()
            Spacer(Modifier.height(heroToButtonsSpacing))
            SocialLoginButtons(
                onSocialLoginClick = onSocialLoginClick,
                providers = providers,
                enabled = !shouldShowLoadingOverlay,
            )
            LoginStatus(
                errorMessage = uiState.errorMessage,
            )
        }

        if (shouldShowLoadingOverlay) {
            LoginLoadingOverlay()
        }
    }
}

@Preview(name = "Login Dark", widthDp = 402, heightDp = 941)
@Composable
private fun LoginScreenDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        LoginScreen(
            onSocialLoginClick = {},
            uiState = LoginUiState(),
        )
    }
}

@Preview(name = "Login Light", widthDp = 402, heightDp = 941)
@Composable
private fun LoginScreenLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        LoginScreen(
            onSocialLoginClick = {},
            uiState = LoginUiState(
                errorMessage = "로그인하지 못했어요. 다시 시도해 주세요.",
            ),
        )
    }
}

@Preview(name = "Login Loading Light", widthDp = 402, heightDp = 941)
@Composable
private fun LoginScreenLoadingLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        LoginScreen(
            onSocialLoginClick = {},
            uiState = LoginUiState(isLoading = true),
        )
    }
}

@Preview(name = "Login Loading Dark", widthDp = 402, heightDp = 941)
@Composable
private fun LoginScreenLoadingDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        LoginScreen(
            onSocialLoginClick = {},
            uiState = LoginUiState(isLoading = true),
        )
    }
}

@Preview(name = "Login Small Screen", widthDp = 320, heightDp = 568)
@Composable
private fun LoginScreenSmallPreview() {
    RingoutTheme(ThemeMode.Dark) {
        LoginScreen(
            onSocialLoginClick = {},
            uiState = LoginUiState(),
        )
    }
}

@Preview(name = "Android Login", widthDp = 402, heightDp = 941)
@Composable
private fun LoginScreenAndroidPreview() {
    RingoutTheme(ThemeMode.Dark) {
        LoginScreen(
            uiState = LoginUiState(),
            onSocialLoginClick = {},
            providers = listOf(SocialLoginProvider.Google, SocialLoginProvider.Kakao),
        )
    }
}

@Preview(name = "iOS Login", widthDp = 402, heightDp = 941)
@Composable
private fun LoginScreenIosPreview() {
    RingoutTheme(ThemeMode.Dark) {
        LoginScreen(
            uiState = LoginUiState(),
            onSocialLoginClick = {},
            providers = listOf(
                SocialLoginProvider.Apple,
                SocialLoginProvider.Google,
                SocialLoginProvider.Kakao,
            ),
        )
    }
}

internal val LoginUiState.shouldShowLoadingOverlay: Boolean
    get() = isLoading || completion != null
