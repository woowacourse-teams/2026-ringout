package com.joon.ringout.domain.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthSessionState {
    Restoring,
    Unauthenticated,
    Authenticated,
    ReauthenticationRequired,
}

class AuthSession {
    private val mutableState = MutableStateFlow(AuthSessionState.Restoring)
    private val mutableIdentity = MutableStateFlow<Any?>(null)

    val state: StateFlow<AuthSessionState> = mutableState.asStateFlow()
    // 토큰 갱신과 별개로 로그인 계정의 수명을 구분한다.
    val identity: StateFlow<Any?> = mutableIdentity.asStateFlow()

    fun startNewSession() {
        mutableIdentity.value = Any()
        markAuthenticated()
    }

    fun markAuthenticated() {
        mutableIdentity.compareAndSet(null, Any())
        mutableState.value = AuthSessionState.Authenticated
    }

    fun requireReauthentication() {
        mutableIdentity.value = null
        mutableState.value = AuthSessionState.ReauthenticationRequired
    }

    fun clear() {
        mutableIdentity.value = null
        mutableState.value = AuthSessionState.Unauthenticated
    }
}

private val sharedAuthSession: AuthSession by lazy(::AuthSession)

internal fun getAuthSession(): AuthSession = sharedAuthSession
