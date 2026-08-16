package com.joon.ringout.data.member

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.joon.ringout.data.auth.local.rememberSecureTokenStorage
import com.joon.ringout.data.network.createRingoutHttpClient
import com.joon.ringout.domain.member.MemberRepository

@Composable
fun rememberMemberRepository(): MemberRepository {
    val tokenStorage = rememberSecureTokenStorage()
    val httpClient = remember { createRingoutHttpClient() }
    DisposableEffect(httpClient) {
        onDispose(httpClient::close)
    }
    return remember(httpClient, tokenStorage) {
        DefaultMemberRepository(httpClient, tokenStorage)
    }
}
