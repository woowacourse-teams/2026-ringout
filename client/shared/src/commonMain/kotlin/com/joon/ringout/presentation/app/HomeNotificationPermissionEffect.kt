package com.joon.ringout.presentation.app

import androidx.compose.runtime.Composable
import com.joon.ringout.alarm.PushNotificationPermissionEffect
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.navigation.AppRoute

/** Keeps the first notification request available to users who completed the older onboarding. */
@Composable
internal fun HomeNotificationPermissionEffect(
    authSessionState: AuthSessionState,
    displayedRoute: AppRoute,
    isAlarmCreationGuidePending: Boolean,
    isServiceBlocked: Boolean,
) {
    PushNotificationPermissionEffect(
        isEligible = authSessionState == AuthSessionState.Authenticated &&
            displayedRoute == AppRoute.Home &&
            !isAlarmCreationGuidePending &&
            !isServiceBlocked,
    )
}
