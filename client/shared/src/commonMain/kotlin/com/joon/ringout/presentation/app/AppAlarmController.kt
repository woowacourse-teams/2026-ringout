package com.joon.ringout.presentation.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.joon.ringout.alarm.AlarmController
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.rememberAlarmController
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.navigation.AppNavigationState
import com.joon.ringout.presentation.navigation.AppRoute

@Composable
internal fun rememberAppAlarmController(
    navigationState: AppNavigationState,
    editorRoute: AppRoute?,
    alarmSetupViewModel: AlarmSetupViewModel?,
    homeViewModel: HomeViewModel,
): AlarmController {
    val callbacks = remember(
        navigationState,
        editorRoute,
        alarmSetupViewModel,
        homeViewModel,
    ) {
        AppAlarmControllerCallbacks(
            navigationState = navigationState,
            editorRoute = editorRoute,
            alarmSetupViewModel = alarmSetupViewModel,
            homeViewModel = homeViewModel,
        )
    }
    return rememberAlarmController(
        onSaveCompleted = callbacks::onSaveCompleted,
        onSaveError = callbacks::onSaveError,
        onError = callbacks::onError,
    )
}

@Composable
internal fun AlarmControllerInitializationEffect(alarmController: AlarmController) {
    LaunchedEffect(alarmController) {
        alarmController.ensureFullScreenAccess()
    }
}

internal class AppAlarmControllerCallbacks(
    private val navigationState: AppNavigationState,
    private val editorRoute: AppRoute?,
    private val alarmSetupViewModel: AlarmSetupViewModel?,
    private val homeViewModel: HomeViewModel,
) {
    fun onSaveCompleted(request: AlarmScheduleRequest) {
        if (
            editorRoute != null && navigationState.editorRoute == editorRoute &&
            alarmSetupViewModel?.onSaveCompleted(request) == true
        ) {
            navigationState.navigate(AppRoute.Home)
        }
    }

    fun onSaveError(request: AlarmScheduleRequest, message: String) {
        if (navigationState.editorRoute == editorRoute) {
            alarmSetupViewModel?.onSaveError(request, message)
        }
    }

    fun onError(message: String) {
        homeViewModel.showError(message)
    }
}
