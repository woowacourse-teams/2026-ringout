package com.joon.ringout.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimensions.contentHorizontalPadding)
                .then(backgroundSemantics),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(dimensions.contentTopSpacing))
            LoginHero()
            Spacer(Modifier.height(dimensions.heroToButtonsSpacing))
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
