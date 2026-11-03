package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.joon.ringout.analytics.AnalyticsAuthProvider

@Composable
internal fun LoginRoute(
    viewModel: LoginViewModel,
    isActive: Boolean,
    onAuthenticated: (Long, Long) -> Boolean,
    onSignupRequired: (Long, Long, String, AnalyticsAuthProvider) -> Boolean,
    onLoginStarted: () -> Long = { 0L },
    onLoginAttemptRejected: (Long) -> Unit = {},
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
    LaunchedEffect(viewModel, completion, isActive) {
        if (!isActive) return@LaunchedEffect
        val handled = when (completion) {
            is LoginCompletion.Authenticated -> onAuthenticated(
                completion.attemptId,
                completion.eventId,
            )
            is LoginCompletion.SignupRequired -> onSignupRequired(
                completion.attemptId,
                completion.eventId,
                completion.signupToken,
                completion.provider,
            )
            null -> return@LaunchedEffect
        }
        if (handled) viewModel.consumeCompletion(completion.eventId)
    }

    LoginScreen(
        uiState = uiState,
        onSocialLoginClick = { provider ->
            if (isActive) {
                when (provider) {
                    SocialLoginProvider.Apple -> {
                        if (!viewModel.uiState.isLoading) {
                            val attemptId = onLoginStarted()
                            if (viewModel.beginAppleSignIn(attemptId)) {
                                launchAppleSignIn()
                            } else {
                                onLoginAttemptRejected(attemptId)
                            }
                        }
                    }

                    SocialLoginProvider.Google -> {
                        if (!viewModel.uiState.isLoading) {
                            val attemptId = onLoginStarted()
                            if (viewModel.beginGoogleSignIn(attemptId)) {
                                launchGoogleSignIn()
                            } else {
                                onLoginAttemptRejected(attemptId)
                            }
                        }
                    }

                    SocialLoginProvider.Kakao -> {
                        if (!viewModel.uiState.isLoading) {
                            val attemptId = onLoginStarted()
                            if (viewModel.beginKakaoSignIn(attemptId)) {
                                launchKakaoSignIn()
                            } else {
                                onLoginAttemptRejected(attemptId)
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier,
    )
}
