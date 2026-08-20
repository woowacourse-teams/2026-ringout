package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
internal actual fun rememberAppleIdTokenLauncher(
    onResult: (AppleIdTokenResult) -> Unit,
): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember {
        {
            currentOnResult.value(
                AppleIdTokenResult.Failure("Apple 로그인은 iOS에서만 사용할 수 있어요."),
            )
        }
    }
}
