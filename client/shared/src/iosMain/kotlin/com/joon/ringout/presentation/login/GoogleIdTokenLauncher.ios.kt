package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.joon.ringout.platform.IosGoogleSignInCallback
import com.joon.ringout.platform.LocalIosNativeServices

@Composable
internal actual fun rememberGoogleIdTokenLauncher(
    serverClientId: String,
    onResult: (GoogleIdTokenResult) -> Unit,
): () -> Unit {
    val nativeServices = LocalIosNativeServices.current
    val currentOnResult by rememberUpdatedState(onResult)
    val callback = remember {
        object : IosGoogleSignInCallback {
            override fun onSuccess(idToken: String) {
                currentOnResult(GoogleIdTokenResult.Success(idToken))
            }

            override fun onCancelled() {
                currentOnResult(GoogleIdTokenResult.Cancelled)
            }

            override fun onFailure(message: String) {
                currentOnResult(GoogleIdTokenResult.Failure(message))
            }
        }
    }
    return remember(nativeServices, callback) {
        { nativeServices.googleSignInService().signIn(callback) }
    }
}
