package com.joon.ringout.presentation.common

import com.joon.ringout.AppScreen
import com.joon.ringout.canShowAppDialog
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.common.component.AppMessageHostState

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
    screen: AppScreen,
    authSessionState: AuthSessionState,
    homeErrorMessage: String?,
    alarmSetupErrorMessage: String?,
    destinationErrorMessage: String?,
): PendingAppMessage? {
    if (!canShowAppDialog(screen, authSessionState)) return null

    return when {
        screen == AppScreen.Home && homeErrorMessage != null ->
            PendingAppMessage(
                source = AppMessageSource.Home,
                state = AppMessageHostState(
                    title = "알람을 처리할 수 없습니다",
                    message = homeErrorMessage,
                ),
            )

        screen in alarmSetupMessageScreens && alarmSetupErrorMessage != null ->
            PendingAppMessage(
                source = AppMessageSource.AlarmSetup,
                state = AppMessageHostState(
                    title = "알람을 처리할 수 없습니다",
                    message = alarmSetupErrorMessage,
                ),
            )

        screen == AppScreen.Destination && destinationErrorMessage != null ->
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

private val alarmSetupMessageScreens = setOf(
    AppScreen.AddAlarm,
    AppScreen.EditAlarm,
    AppScreen.Destination,
    AppScreen.AlarmSound,
)
