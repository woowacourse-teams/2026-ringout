package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.home.HomeViewModel
import com.joon.ringout.presentation.mypage.MyPageViewModel
import com.joon.ringout.presentation.signup.SignupViewModel

/** 재인증이 필요한 동안 진행 상태를 정리하고 로그인 백스택을 유지한다. */
@Composable
internal fun ReauthenticationNavigationEffect(
    authSessionState: AuthSessionState,
    navigationState: AppNavigationState,
    homeViewModel: HomeViewModel,
    signupViewModel: SignupViewModel?,
    alarmSetupViewModel: AlarmSetupViewModel?,
    myPageViewModel: MyPageViewModel?,
    destinationViewModel: DestinationViewModel?,
) {
    // 재인증 중 새로 연결된 스코프, 늦은 오류, 다른 화면 이동 요청에도 정책을 다시 적용한다.
    LaunchedEffect(
        authSessionState,
        navigationState,
        homeViewModel,
        signupViewModel,
        alarmSetupViewModel,
        myPageViewModel,
        destinationViewModel,
        navigationState.backStack.toList(),
        navigationState.requestedRoute,
        alarmSetupViewModel?.uiState,
        alarmSetupViewModel?.permissionDialog,
        destinationViewModel?.uiState?.errorMessage,
        homeViewModel.uiState.errorMessage,
    ) {
        if (authSessionState != AuthSessionState.ReauthenticationRequired) return@LaunchedEffect

        // 화면 스코프가 제거되기 전에 민감한 진행 상태와 완료 콜백을 먼저 무효화한다.
        signupViewModel?.resetSignup()
        alarmSetupViewModel?.resetSaveFlow()
        homeViewModel.clearError()
        myPageViewModel?.resetAccountActionFlow()
        if (destinationViewModel?.uiState?.errorMessage != null) {
            destinationViewModel.clearError()
        }
        // 알람 울림의 표시 우선순위는 유지하면서, 그 아래의 복귀 경로를 로그인으로 정리한다.
        if (!navigationState.isCurrentRoute(AppRoute.Login)) {
            navigationState.navigate(AppRoute.Login)
        }
    }
}
