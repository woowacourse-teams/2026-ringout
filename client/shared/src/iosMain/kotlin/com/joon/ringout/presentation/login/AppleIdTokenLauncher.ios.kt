package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
internal actual fun rememberAppleIdTokenLauncher(
    onResult: (AppleIdTokenResult) -> Unit,
): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    // TODO(RINGOUT_ACCOUNT): 로그인 재도입 시 Apple 인증 launcher를 복구한다.
    return remember {
        {
            currentOnResult.value(
                AppleIdTokenResult.Failure("로그인은 현재 버전에서 지원하지 않아요."),
            )
        }
    }
}
