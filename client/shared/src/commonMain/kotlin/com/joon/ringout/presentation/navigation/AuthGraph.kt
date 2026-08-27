package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.AppScreen
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.login.LoginRoute
import com.joon.ringout.presentation.termsagreement.TermsAgreementRoute

internal fun EntryProviderScope<AppRoute>.authGraph(
    authNavigation: AuthNavigation,
    screen: AppScreen,
    authSessionState: AuthSessionState,
) {
    entry<AppRoute.Login>(clazzContentKey = AppRoute::viewModelStoreKey) {
        LoginRoute(
            viewModel = authNavigation.loginViewModel,
            isActive = authNavigation.isActive(AppRoute.Login, screen),
            onBackClick = { authNavigation.onBack(AppRoute.Login, screen, authSessionState) },
            onAuthenticated = { authNavigation.onAuthenticated(screen) },
            onSignupRequired = { token, provider ->
                authNavigation.onSignupRequired(screen, token, provider)
            },
        )
    }

    entry<AppRoute.TermsAgreement>(clazzContentKey = AppRoute::viewModelStoreKey) {
        TermsAgreementRoute(
            signupViewModel = authNavigation.signupViewModel,
            isActive = authNavigation.isActive(AppRoute.TermsAgreement, screen),
            onMissingSignup = { authNavigation.onMissingSignup(screen) },
            onSignupCompleted = { authNavigation.onSignupCompleted(screen) },
        )
    }
}
