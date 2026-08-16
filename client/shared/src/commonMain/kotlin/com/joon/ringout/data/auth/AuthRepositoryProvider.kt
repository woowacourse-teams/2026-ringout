package com.joon.ringout.data.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.joon.ringout.data.auth.local.rememberSecureTokenStorage
import com.joon.ringout.data.auth.remote.KtorAuthApi
import com.joon.ringout.data.network.createRingoutHttpClient
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthSession

@Composable
fun rememberAuthRepository(authSession: AuthSession): AuthRepository {
    val tokenStorage = rememberSecureTokenStorage()
    val httpClient = remember { createRingoutHttpClient() }
    DisposableEffect(httpClient) {
        onDispose(httpClient::close)
    }
    return remember(httpClient, tokenStorage, authSession) {
        DefaultAuthRepository(
            authApi = KtorAuthApi(httpClient),
            tokenStorage = tokenStorage,
            authSession = authSession,
        )
    }
}
