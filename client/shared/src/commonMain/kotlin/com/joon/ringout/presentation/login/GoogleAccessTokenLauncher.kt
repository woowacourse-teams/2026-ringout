package com.joon.ringout.presentation.login

sealed interface GoogleAccessTokenResult {
    data class Success(
        val accessToken: String,
    ) : GoogleAccessTokenResult

    data object Cancelled : GoogleAccessTokenResult

    data class Failure(
        val message: String,
    ) : GoogleAccessTokenResult
}

@androidx.compose.runtime.Composable
internal expect fun rememberGoogleAccessTokenLauncher(
    onResult: (GoogleAccessTokenResult) -> Unit,
): () -> Unit
