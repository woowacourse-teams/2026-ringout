package com.joon.ringout.analytics

import com.joon.ringout.domain.auth.AuthSessionState

internal fun AuthSessionState.toAnalyticsLoginStateOrNull(): AnalyticsLoginState? = when (this) {
    AuthSessionState.Restoring -> null
    AuthSessionState.Unauthenticated -> AnalyticsLoginState.LoggedOut
    AuthSessionState.ReauthenticationRequired -> AnalyticsLoginState.LoggedOut
    AuthSessionState.Authenticated -> AnalyticsLoginState.LoggedIn
}
