package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.presentation.login.LoginRoute
import com.joon.ringout.presentation.termsagreement.TermsAgreementRoute

internal fun EntryProviderScope<AppRoute>.authGraph(
    authNavigation: AuthNavigation,
    displayedRoute: AppRoute,
) {
    entry<AppRoute.Login>(clazzContentKey = AppRoute::viewModelStoreKey) {
        LoginRoute(
            viewModel = authNavigation.loginViewModel,
            isActive = authNavigation.isActive(AppRoute.Login, displayedRoute),
            onAuthenticated = { attemptId, eventId ->
                authNavigation.onAuthenticated(displayedRoute, attemptId, eventId)
            },
            onSignupRequired = { attemptId, eventId, token, provider ->
                authNavigation.onSignupRequired(
                    displayedRoute,
                    attemptId,
                    eventId,
                    token,
                    provider,
                )
            },
            onLoginStarted = authNavigation::onLoginStarted,
            onLoginAttemptRejected = authNavigation::onLoginAttemptRejected,
        )
    }

    entry<AppRoute.TermsAgreement>(clazzContentKey = AppRoute::viewModelStoreKey) {
        TermsAgreementRoute(
            signupViewModel = authNavigation.signupViewModel,
            isActive = authNavigation.isActive(AppRoute.TermsAgreement, displayedRoute),
            onMissingSignup = { authNavigation.onMissingSignup(displayedRoute) },
            onSignupCompleted = { attemptId, eventId ->
                authNavigation.onSignupCompleted(displayedRoute, attemptId, eventId)
            },
        )
    }
}
