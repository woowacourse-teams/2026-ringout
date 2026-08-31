package com.joon.ringout.analytics

import com.joon.ringout.domain.auth.AuthSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnalyticsLoginStateMapperTest {
    @Test
    fun `authenticated session maps to logged in`() {
        assertEquals(
            AnalyticsLoginState.LoggedIn,
            AuthSessionState.Authenticated.toAnalyticsLoginStateOrNull(),
        )
    }

    @Test
    fun `unauthenticated session maps to logged out`() {
        assertEquals(
            AnalyticsLoginState.LoggedOut,
            AuthSessionState.Unauthenticated.toAnalyticsLoginStateOrNull(),
        )
    }

    @Test
    fun `restoring session does not produce an analytics login state`() {
        assertNull(AuthSessionState.Restoring.toAnalyticsLoginStateOrNull())
    }

    @Test
    fun `reauthentication required session maps to logged out`() {
        assertEquals(
            AnalyticsLoginState.LoggedOut,
            AuthSessionState.ReauthenticationRequired.toAnalyticsLoginStateOrNull(),
        )
    }
}
