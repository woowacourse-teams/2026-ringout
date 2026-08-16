package com.joon.ringout.data.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.joon.ringout.data.auth.local.rememberSecureTokenStorage
import com.joon.ringout.data.auth.remote.KtorAuthApi
import com.joon.ringout.data.network.createRingoutHttpClient
import com.joon.ringout.domain.auth.AuthRepository

@Composable
fun rememberAuthRepository(): AuthRepository {
    val tokenStorage = rememberSecureTokenStorage()
    val httpClient = remember { createRingoutHttpClient() }
    DisposableEffect(httpClient) {
        onDispose(httpClient::close)
    }
    return remember(httpClient, tokenStorage) {
        DefaultAuthRepository(
            authApi = KtorAuthApi(httpClient),
            tokenStorage = tokenStorage,
        )
    }
}
