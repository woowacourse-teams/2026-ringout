package com.joon.ringout.presentation.login

import androidx.compose.foundation.background
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.destination.PlatformBackHandler
import com.joon.ringout.presentation.login.component.LoginHeader
import com.joon.ringout.presentation.login.component.LoginHero
import com.joon.ringout.presentation.login.component.SocialLoginButtons
import com.joon.ringout.presentation.login.component.loginColors
import com.joon.ringout.presentation.login.component.loginDimensions

enum class SocialLoginProvider {
    Google,
    Kakao,
    Naver,
}

@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onSocialLoginClick: (SocialLoginProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(onBack = onBackClick)
    LoginScreenContent(
        onBackClick = onBackClick,
        onSocialLoginClick = onSocialLoginClick,
        modifier = modifier,
    )
}

@Composable
internal fun LoginScreenContent(
    onBackClick: () -> Unit,
    onSocialLoginClick: (SocialLoginProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = loginColors()
    val dimensions = loginDimensions()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoginHeader(onBackClick = onBackClick)
        Spacer(Modifier.height(dimensions.headerContainerToImageSpacing))
        LoginHero()
        Spacer(Modifier.height(dimensions.textToSocialSpacing))
        SocialLoginButtons(onSocialLoginClick = onSocialLoginClick)
    }
}

@Preview(name = "Login Dark", widthDp = 402, heightDp = 941)
@Composable
private fun LoginScreenDarkPreview() {
    RingoutTheme(ThemeMode.Dark) {
        LoginScreenContent(
            onBackClick = {},
            onSocialLoginClick = {},
        )
    }
}

@Preview(name = "Login Light", widthDp = 402, heightDp = 941)
@Composable
private fun LoginScreenLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        LoginScreenContent(
            onBackClick = {},
            onSocialLoginClick = {},
        )
    }
}
