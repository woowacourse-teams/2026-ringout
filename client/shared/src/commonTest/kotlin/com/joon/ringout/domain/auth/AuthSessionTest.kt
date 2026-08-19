package com.joon.ringout.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthSessionTest {
    @Test
    fun `token expiration requires reauthentication`() {
        val session = AuthSession().apply { markAuthenticated() }

        session.requireReauthentication()

        assertEquals(AuthSessionState.ReauthenticationRequired, session.state.value)
    }

    @Test
    fun `successful authentication clears reauthentication requirement`() {
        val session = AuthSession().apply { requireReauthentication() }

        session.markAuthenticated()

        assertEquals(AuthSessionState.Authenticated, session.state.value)
    }

    @Test
    fun `ordinary logout remains unauthenticated`() {
        val session = AuthSession().apply { markAuthenticated() }

        session.clear()

        assertEquals(AuthSessionState.Unauthenticated, session.state.value)
    }
}
