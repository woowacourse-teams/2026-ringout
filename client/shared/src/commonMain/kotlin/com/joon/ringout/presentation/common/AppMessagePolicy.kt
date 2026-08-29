package com.joon.ringout.presentation.common

import com.joon.ringout.canShowAppDialog
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.common.component.AppMessageHostState
import com.joon.ringout.presentation.navigation.AppRoute

internal enum class AppMessageSource {
    Home,
    AlarmSetup,
    Destination,
}

internal data class PendingAppMessage(
    val source: AppMessageSource,
    val state: AppMessageHostState,
)

internal fun resolveAppMessage(
    displayedRoute: AppRoute,
    authSessionState: AuthSessionState,
    homeErrorMessage: String?,
    alarmSetupErrorMessage: String?,
    destinationErrorMessage: String?,
): PendingAppMessage? {
    if (!canShowAppDialog(displayedRoute, authSessionState)) return null

    return when {
        displayedRoute == AppRoute.Home && homeErrorMessage != null ->
            PendingAppMessage(
                source = AppMessageSource.Home,
                state = AppMessageHostState(
                    title = "알람을 처리할 수 없습니다",
                    message = homeErrorMessage,
                ),
            )

        displayedRoute.isAlarmSetupMessageRoute() && alarmSetupErrorMessage != null ->
            PendingAppMessage(
                source = AppMessageSource.AlarmSetup,
                state = AppMessageHostState(
                    title = "알람을 처리할 수 없습니다",
                    message = alarmSetupErrorMessage,
                ),
            )

        displayedRoute is AppRoute.Destination && destinationErrorMessage != null ->
            PendingAppMessage(
                source = AppMessageSource.Destination,
                state = AppMessageHostState(
                    title = "목적지를 처리할 수 없습니다",
                    message = destinationErrorMessage,
                ),
            )

        else -> null
    }
}

private fun AppRoute.isAlarmSetupMessageRoute(): Boolean = when (this) {
    AppRoute.AddAlarm,
    AppRoute.AlarmSound,
    is AppRoute.EditAlarm,
    is AppRoute.Destination,
    -> true
    else -> false
}
