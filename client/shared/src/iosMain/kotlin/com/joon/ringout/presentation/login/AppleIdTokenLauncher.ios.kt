package com.joon.ringout.presentation.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.joon.ringout.platform.IosAppleSignInCallback
import com.joon.ringout.platform.LocalIosNativeServices

@Composable
internal actual fun rememberAppleIdTokenLauncher(
    onResult: (AppleIdTokenResult) -> Unit,
): () -> Unit {
    val nativeServices = LocalIosNativeServices.current
    val currentOnResult by rememberUpdatedState(onResult)
    val callback = remember {
        object : IosAppleSignInCallback {
            override fun onSuccess(idToken: String) {
                currentOnResult(AppleIdTokenResult.Success(idToken))
            }

            override fun onCancelled() {
                currentOnResult(AppleIdTokenResult.Cancelled)
            }

            override fun onFailure(message: String) {
                currentOnResult(AppleIdTokenResult.Failure(message))
            }
        }
    }
    return remember(nativeServices, callback) {
        { nativeServices.appleSignInService().signIn(callback) }
    }
}
