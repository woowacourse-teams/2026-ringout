package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.joon.ringout.platform.IosGoogleSignInCallback
import com.joon.ringout.platform.LocalIosNativeServices

@Composable
internal actual fun rememberGoogleAccessTokenLauncher(
    onResult: (GoogleAccessTokenResult) -> Unit,
): () -> Unit {
    val nativeServices = LocalIosNativeServices.current
    val currentOnResult by rememberUpdatedState(onResult)
    val callback = remember {
        object : IosGoogleSignInCallback {
            override fun onSuccess(accessToken: String) {
                currentOnResult(GoogleAccessTokenResult.Success(accessToken))
            }

            override fun onCancelled() {
                currentOnResult(GoogleAccessTokenResult.Cancelled)
            }

            override fun onFailure(message: String) {
                currentOnResult(GoogleAccessTokenResult.Failure(message))
            }
        }
    }
    return remember(nativeServices, callback) {
        { nativeServices.googleSignInService().signIn(callback) }
    }
}
