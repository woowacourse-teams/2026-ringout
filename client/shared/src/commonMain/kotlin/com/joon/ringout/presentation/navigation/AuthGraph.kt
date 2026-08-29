package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.login.LoginRoute
import com.joon.ringout.presentation.termsagreement.TermsAgreementRoute

internal fun EntryProviderScope<AppRoute>.authGraph(
    authNavigation: AuthNavigation,
    displayedRoute: AppRoute,
    authSessionState: AuthSessionState,
) {
    entry<AppRoute.Login>(clazzContentKey = AppRoute::viewModelStoreKey) {
        LoginRoute(
            viewModel = authNavigation.loginViewModel,
            isActive = authNavigation.isActive(AppRoute.Login, displayedRoute),
            onBackClick = {
                authNavigation.onBack(AppRoute.Login, displayedRoute, authSessionState)
            },
            onAuthenticated = { authNavigation.onAuthenticated(displayedRoute) },
            onSignupRequired = { token, provider ->
                authNavigation.onSignupRequired(displayedRoute, token, provider)
            },
        )
    }

    entry<AppRoute.TermsAgreement>(clazzContentKey = AppRoute::viewModelStoreKey) {
        TermsAgreementRoute(
            signupViewModel = authNavigation.signupViewModel,
            isActive = authNavigation.isActive(AppRoute.TermsAgreement, displayedRoute),
            onMissingSignup = { authNavigation.onMissingSignup(displayedRoute) },
            onSignupCompleted = { authNavigation.onSignupCompleted(displayedRoute) },
        )
    }
}
