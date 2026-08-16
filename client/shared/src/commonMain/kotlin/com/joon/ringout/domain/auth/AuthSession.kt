package com.joon.ringout.domain.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthSessionState {
    Unauthenticated,
    Authenticated,
}

class AuthSession {
    private val mutableState = MutableStateFlow(AuthSessionState.Unauthenticated)

    val state: StateFlow<AuthSessionState> = mutableState.asStateFlow()

    fun markAuthenticated() {
        mutableState.value = AuthSessionState.Authenticated
    }

    fun clear() {
        mutableState.value = AuthSessionState.Unauthenticated
    }
}
