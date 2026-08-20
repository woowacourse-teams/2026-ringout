package com.joon.ringout.presentation.login

sealed interface AppleIdTokenResult {
    data class Success(
        val idToken: String,
    ) : AppleIdTokenResult

    data object Cancelled : AppleIdTokenResult

    data class Failure(
        val message: String,
    ) : AppleIdTokenResult
}

@androidx.compose.runtime.Composable
internal expect fun rememberAppleIdTokenLauncher(
    onResult: (AppleIdTokenResult) -> Unit,
): () -> Unit
