package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.joon.ringout.analytics.AnalyticsAuthProvider

@Composable
internal fun LoginRoute(
    viewModel: LoginViewModel,
    isActive: Boolean,
    onBackClick: () -> Unit,
    onAuthenticated: () -> Boolean,
    onSignupRequired: (String, AnalyticsAuthProvider) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState
    val launchAppleSignIn = rememberAppleIdTokenLauncher(
        onResult = viewModel::handleAppleIdTokenResult,
    )
    val launchGoogleSignIn = rememberGoogleAccessTokenLauncher(
        onResult = viewModel::handleGoogleAccessTokenResult,
    )
    val launchKakaoSignIn = rememberKakaoAccessTokenLauncher(
        onResult = viewModel::handleKakaoAccessTokenResult,
    )
    val completion = uiState.completion
    LaunchedEffect(completion, isActive) {
        if (!isActive) return@LaunchedEffect
        val handled = when (completion) {
            is LoginCompletion.Authenticated -> onAuthenticated()
            is LoginCompletion.SignupRequired -> onSignupRequired(
                completion.signupToken,
                completion.provider,
            )
            null -> return@LaunchedEffect
        }
        if (handled) viewModel.consumeCompletion(completion.eventId)
    }

    LoginScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onSocialLoginClick = { provider ->
            if (isActive) {
                when (provider) {
                    SocialLoginProvider.Apple -> {
                        if (viewModel.beginAppleSignIn()) launchAppleSignIn()
                    }

                    SocialLoginProvider.Google -> {
                        if (viewModel.beginGoogleSignIn()) launchGoogleSignIn()
                    }

                    SocialLoginProvider.Kakao -> {
                        if (viewModel.beginKakaoSignIn()) launchKakaoSignIn()
                    }
                }
            }
        },
        modifier = modifier,
    )
}
