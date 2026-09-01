package com.joon.ringout.presentation.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.login.component.LoginHeader
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
    onBackClick: () -> Unit,
    onSocialLoginClick: (SocialLoginProvider) -> Unit,
    modifier: Modifier = Modifier,
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
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .then(backgroundSemantics),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoginHeader(
                onBackClick = {
                    if (!shouldShowLoadingOverlay) onBackClick()
                },
            )
            Spacer(Modifier.height(dimensions.headerContainerToImageSpacing))
            LoginHero()
            Spacer(Modifier.height(dimensions.textToSocialSpacing))
            SocialLoginButtons(
                onSocialLoginClick = onSocialLoginClick,
                enabled = !shouldShowLoadingOverlay,
            )
            LoginStatus(
                errorMessage = uiState.errorMessage,
            )
        }

        if (shouldShowLoadingOverlay) {
            LoginLoadingOverlay(Modifier.matchParentSize())
        }
    }
}

@Preview(name = "Login Dark", widthDp = 402, heightDp = 941)
@Composable
private fun LoginScreenDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        LoginScreen(
            onBackClick = {},
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
            onBackClick = {},
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
            onBackClick = {},
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
            onBackClick = {},
            onSocialLoginClick = {},
            uiState = LoginUiState(isLoading = true),
        )
    }
}

internal val LoginUiState.shouldShowLoadingOverlay: Boolean
    get() = isLoading || completion != null
