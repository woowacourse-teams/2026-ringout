package com.joon.ringout.presentation.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.common.AppMessageSource
import com.joon.ringout.presentation.common.component.AppMessageHost
import com.joon.ringout.presentation.common.resolveAppMessage
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.navigation.AppRoute

@Composable
internal fun AppMessageDialogHost(
    displayedRoute: AppRoute,
    authSessionState: AuthSessionState,
    homeViewModel: HomeViewModel,
    alarmSetupViewModel: AlarmSetupViewModel?,
    destinationViewModel: DestinationViewModel?,
) {
    val pendingMessage = resolveAppMessage(
        displayedRoute = displayedRoute,
        authSessionState = authSessionState,
        homeErrorMessage = homeViewModel.uiState.errorMessage,
        alarmSetupErrorMessage = alarmSetupViewModel?.uiState?.errorMessage,
        destinationErrorMessage = destinationViewModel?.uiState?.errorMessage,
    )
    val dismissHandler = remember(homeViewModel, alarmSetupViewModel, destinationViewModel) {
        AppMessageDismissHandler(
            onHomeErrorDismissed = homeViewModel::clearError,
            onAlarmSetupErrorDismissed = { alarmSetupViewModel?.clearError() },
            onDestinationErrorDismissed = { destinationViewModel?.clearError() },
        )
    }
    AppMessageHost(
        state = pendingMessage?.state,
        onDismiss = { dismissHandler.dismiss(pendingMessage?.source) },
    )
}

internal class AppMessageDismissHandler(
    private val onHomeErrorDismissed: () -> Unit,
    private val onAlarmSetupErrorDismissed: () -> Unit,
    private val onDestinationErrorDismissed: () -> Unit,
) {
    fun dismiss(source: AppMessageSource?) {
        when (source) {
            AppMessageSource.Home -> onHomeErrorDismissed()
            AppMessageSource.AlarmSetup -> onAlarmSetupErrorDismissed()
            AppMessageSource.Destination -> onDestinationErrorDismissed()
            null -> Unit
        }
    }
}
