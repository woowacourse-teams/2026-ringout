package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable

sealed interface GoogleIdTokenResult {
    data class Success(
        val idToken: String,
    ) : GoogleIdTokenResult

    data object Cancelled : GoogleIdTokenResult

    data class Failure(
        val message: String,
    ) : GoogleIdTokenResult
}

@Composable
internal expect fun rememberGoogleIdTokenLauncher(
    serverClientId: String,
    onResult: (GoogleIdTokenResult) -> Unit,
): () -> Unit
