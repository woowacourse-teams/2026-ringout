package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joon.ringout.AppScreen
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

/** Connects auth results to navigation without saving credentials in route keys. */
internal class AuthNavigation(
    private val navigationState: AppNavigationState,
    val loginViewModel: LoginViewModel,
    val signupViewModel: SignupViewModel,
) {
    fun isActive(route: AppRoute, screen: AppScreen): Boolean =
        navigationState.isCurrentRoute(route) && navigationState.requestedScreen == screen

    fun isBackBlocked(screen: AppScreen, authSessionState: AuthSessionState): Boolean =
        when (screen) {
            AppScreen.Login ->
                authSessionState == AuthSessionState.ReauthenticationRequired ||
                    !isActive(AppRoute.Login, screen) ||
                    loginViewModel.uiState.shouldShowLoadingOverlay
            AppScreen.TermsAgreement ->
                signupViewModel.uiState.isSaving || signupViewModel.uiState.completedEventId != null
            else -> false
        }

    fun onBack(route: AppRoute, screen: AppScreen, authSessionState: AuthSessionState) {
        if (!isActive(route, screen) || isBackBlocked(screen, authSessionState)) return
        if (route == AppRoute.TermsAgreement) signupViewModel.resetSignup()
        navigationState.popBackStack(route)
    }

    fun onAuthenticated(screen: AppScreen): Boolean {
        if (!isActive(AppRoute.Login, screen)) return false
        signupViewModel.resetSignup()
        navigationState.navigate(AppRoute.Home)
        return true
    }

    fun onSignupRequired(
        screen: AppScreen,
        signupToken: String,
        provider: AnalyticsAuthProvider,
    ): Boolean {
        if (!isActive(AppRoute.Login, screen)) return false
        signupViewModel.startSignup(signupToken, provider)
        navigationState.navigate(AppRoute.TermsAgreement)
        return true
    }

    fun onSignupCompleted(screen: AppScreen): Boolean {
        if (!isActive(AppRoute.TermsAgreement, screen)) return false
        navigationState.navigate(AppRoute.Home)
        return true
    }

    fun onMissingSignup(screen: AppScreen) {
        if (isActive(AppRoute.TermsAgreement, screen) && !signupViewModel.uiState.hasPendingSignup) {
            navigationState.navigate(AppRoute.Login)
        }
    }
}
