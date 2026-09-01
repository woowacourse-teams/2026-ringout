package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.login.LoginViewModel
import com.joon.ringout.presentation.login.shouldShowLoadingOverlay
import com.joon.ringout.presentation.signup.SignupViewModel

@Composable
internal fun rememberAuthNavigation(
    navigationState: AppNavigationState,
    loginViewModel: LoginViewModel,
    signupViewModel: SignupViewModel,
): AuthNavigation = remember(navigationState, loginViewModel, signupViewModel) {
    AuthNavigation(navigationState, loginViewModel, signupViewModel)
}

/** 경로 키에 인증 정보를 저장하지 않고 인증 결과를 화면 이동에 연결한다. */
internal class AuthNavigation(
    private val navigationState: AppNavigationState,
    val loginViewModel: LoginViewModel,
    val signupViewModel: SignupViewModel,
) {
    fun isActive(route: AppRoute, displayedRoute: AppRoute): Boolean =
        navigationState.isCurrentRoute(route) && displayedRoute == route

    fun isBackBlocked(displayedRoute: AppRoute, authSessionState: AuthSessionState): Boolean =
        when (displayedRoute) {
            AppRoute.Login ->
                authSessionState == AuthSessionState.ReauthenticationRequired ||
                    !isActive(AppRoute.Login, displayedRoute) ||
                    loginViewModel.uiState.shouldShowLoadingOverlay
            AppRoute.TermsAgreement ->
                signupViewModel.uiState.isSaving || signupViewModel.uiState.completedEventId != null
            else -> false
        }

    fun onBack(route: AppRoute, displayedRoute: AppRoute, authSessionState: AuthSessionState) {
        if (
            !isActive(route, displayedRoute) ||
            isBackBlocked(displayedRoute, authSessionState)
        ) {
            return
        }
        if (route == AppRoute.TermsAgreement) signupViewModel.resetSignup()
        navigationState.popBackStack(route)
    }

    fun onAuthenticated(displayedRoute: AppRoute): Boolean {
        if (!isActive(AppRoute.Login, displayedRoute)) return false
        signupViewModel.resetSignup()
        navigationState.navigate(AppRoute.Home)
        return true
    }

    fun onSignupRequired(
        displayedRoute: AppRoute,
        signupToken: String,
        provider: AnalyticsAuthProvider,
    ): Boolean {
        if (!isActive(AppRoute.Login, displayedRoute)) return false
        signupViewModel.startSignup(signupToken, provider)
        navigationState.navigate(AppRoute.TermsAgreement)
        return true
    }

    fun onSignupCompleted(displayedRoute: AppRoute): Boolean {
        if (!isActive(AppRoute.TermsAgreement, displayedRoute)) return false
        navigationState.navigate(AppRoute.Home)
        return true
    }

    fun onMissingSignup(displayedRoute: AppRoute) {
        if (
            isActive(AppRoute.TermsAgreement, displayedRoute) &&
            !signupViewModel.uiState.hasPendingSignup
        ) {
            navigationState.navigate(AppRoute.Login)
        }
    }
}
