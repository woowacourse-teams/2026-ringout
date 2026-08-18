package com.joon.ringout.presentation.login

sealed interface KakaoAccessTokenResult {
    data class Success(
        val accessToken: String,
    ) : KakaoAccessTokenResult

    data object Cancelled : KakaoAccessTokenResult

    data class Failure(
        val message: String,
    ) : KakaoAccessTokenResult
}

@androidx.compose.runtime.Composable
internal expect fun rememberKakaoAccessTokenLauncher(
    onResult: (KakaoAccessTokenResult) -> Unit,
): () -> Unit
