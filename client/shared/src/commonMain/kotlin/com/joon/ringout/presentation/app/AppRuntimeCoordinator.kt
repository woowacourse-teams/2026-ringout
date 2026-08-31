package com.joon.ringout.presentation.app

import androidx.compose.runtime.Composable
import com.joon.ringout.SystemBarAppearanceEffect
import com.joon.ringout.ThemeMode
import com.joon.ringout.alarm.AlarmController
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.alarmsetup.AlarmSetupCoordinator
import com.joon.ringout.presentation.home.HomeAlarmBinding
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.mypage.MyPageViewModel
import com.joon.ringout.presentation.navigation.ActiveMissionNavigationEffect
import com.joon.ringout.presentation.navigation.AlarmEditorNavigation
import com.joon.ringout.presentation.navigation.AlarmEditorNavigationBinding
import com.joon.ringout.presentation.navigation.AppNavigationState
import com.joon.ringout.presentation.navigation.AppRoute
import com.joon.ringout.presentation.navigation.AuthNavigation
import com.joon.ringout.presentation.navigation.MyPageAccountActionCompletionEffect

/** 앱 생명주기 동안 유지할 조정자, 상태 연결, 화면 표시 영역을 연결한다. */
@Composable
internal fun AppRuntimeCoordinator(
    authRepository: AuthRepository,
    authSessionState: AuthSessionState,
    navigationState: AppNavigationState,
    displayedRoute: AppRoute,
    themeMode: ThemeMode,
    activeMissionOccurrenceId: String?,
    homeViewModel: HomeViewModel,
    myPageViewModel: MyPageViewModel?,
    authNavigation: AuthNavigation?,
    alarmEditorNavigation: AlarmEditorNavigation?,
    alarmController: AlarmController,
    missionLocationState: MissionLocationState,
    useSystemLocationPermissionUiOnly: Boolean,
    onRequestWhenInUseLocation: () -> Unit,
    onRequestAlwaysLocation: () -> Unit,
    onConfirmAlwaysLocationResult: () -> Unit,
    onRequestTemporaryFullAccuracy: () -> Unit,
) {
    val signupViewModel = authNavigation?.signupViewModel
    val alarmSetupViewModel = alarmEditorNavigation?.alarmSetupViewModel
    val destinationViewModel = alarmEditorNavigation?.destinationViewModel

    AuthSessionCoordinator(
        authRepository = authRepository,
        authSessionState = authSessionState,
        myPageViewModel = myPageViewModel,
        destinationViewModel = destinationViewModel,
    )
    ReauthenticationCoordinator(
        authSessionState = authSessionState,
        navigationState = navigationState,
        homeViewModel = homeViewModel,
        signupViewModel = signupViewModel,
        alarmSetupViewModel = alarmSetupViewModel,
        myPageViewModel = myPageViewModel,
        destinationViewModel = destinationViewModel,
    )
    MyPageAccountActionCompletionEffect(
        navigationState = navigationState,
        myPageViewModel = myPageViewModel,
        signupViewModel = signupViewModel,
    )
    if (alarmEditorNavigation != null) {
        AlarmEditorNavigationBinding(
            navigation = alarmEditorNavigation,
            displayedRoute = displayedRoute,
        )
    }
    SystemBarAppearanceEffect(
        themeMode = when (displayedRoute) {
            is AppRoute.ActiveAlarmTracking,
            is AppRoute.AlarmRinging,
            -> ThemeMode.Dark
            else -> themeMode
        },
    )
    AlarmSetupCoordinator(
        alarmController = alarmController,
        viewModel = alarmSetupViewModel,
        displayedRoute = displayedRoute,
        authSessionState = authSessionState,
        missionLocationState = missionLocationState,
        useSystemLocationPermissionUiOnly = useSystemLocationPermissionUiOnly,
        onRequestWhenInUseLocation = onRequestWhenInUseLocation,
        onRequestAlwaysLocation = onRequestAlwaysLocation,
        onConfirmAlwaysLocationResult = onConfirmAlwaysLocationResult,
        onRequestTemporaryFullAccuracy = onRequestTemporaryFullAccuracy,
    )
    AlarmControllerInitializationEffect(alarmController)
    HomeAlarmBinding(
        alarmController = alarmController,
        homeViewModel = homeViewModel,
    )
    ActiveMissionNavigationEffect(
        activeMissionOccurrenceId = activeMissionOccurrenceId,
        navigationState = navigationState,
    )
    AppMessageDialogHost(
        displayedRoute = displayedRoute,
        authSessionState = authSessionState,
        homeViewModel = homeViewModel,
        alarmSetupViewModel = alarmSetupViewModel,
        destinationViewModel = destinationViewModel,
    )
}
