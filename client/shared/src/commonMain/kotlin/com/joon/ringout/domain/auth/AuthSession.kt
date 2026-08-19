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

    val state: StateFlow<AuthSessionState> = mutableState.asStateFlow()

    fun markAuthenticated() {
        mutableState.value = AuthSessionState.Authenticated
    }

    fun requireReauthentication() {
        mutableState.value = AuthSessionState.ReauthenticationRequired
    }

    fun clear() {
        mutableState.value = AuthSessionState.Unauthenticated
    }
}

private val sharedAuthSession: AuthSession by lazy(::AuthSession)

internal fun getAuthSession(): AuthSession = sharedAuthSession
