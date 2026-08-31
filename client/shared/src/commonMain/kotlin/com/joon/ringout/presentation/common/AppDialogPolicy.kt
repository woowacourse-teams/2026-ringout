package com.joon.ringout.presentation.common

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.navigation.AppRoute

internal fun canShowAppDialog(
    displayedRoute: AppRoute,
    authSessionState: AuthSessionState,
): Boolean =
    displayedRoute !is AppRoute.AlarmRinging &&
        authSessionState != AuthSessionState.ReauthenticationRequired
