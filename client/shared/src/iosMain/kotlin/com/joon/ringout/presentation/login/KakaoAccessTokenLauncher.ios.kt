package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.joon.ringout.platform.IosKakaoSignInCallback
import com.joon.ringout.platform.LocalIosNativeServices

@Composable
internal actual fun rememberKakaoAccessTokenLauncher(
    onResult: (KakaoAccessTokenResult) -> Unit,
): () -> Unit {
    val nativeServices = LocalIosNativeServices.current
    val currentOnResult by rememberUpdatedState(onResult)
    val callback = remember {
        object : IosKakaoSignInCallback {
            override fun onSuccess(accessToken: String) {
                currentOnResult(KakaoAccessTokenResult.Success(accessToken))
            }

            override fun onCancelled() {
                currentOnResult(KakaoAccessTokenResult.Cancelled)
            }

            override fun onFailure(message: String) {
                currentOnResult(KakaoAccessTokenResult.Failure(message))
            }
        }
    }
    return remember(nativeServices, callback) {
        { nativeServices.kakaoSignInService().signIn(callback) }
    }
}
