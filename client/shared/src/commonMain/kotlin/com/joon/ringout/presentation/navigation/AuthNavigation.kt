package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.appbootstrap.AuthFlowEvent
import com.joon.ringout.presentation.appbootstrap.AuthFlowEventResult
import com.joon.ringout.presentation.appbootstrap.AuthFlowEventType
import com.joon.ringout.presentation.login.LoginViewModel
import com.joon.ringout.presentation.signup.SignupViewModel

@Composable
internal fun rememberAuthNavigation(
    navigationState: AppNavigationState,
    loginViewModel: LoginViewModel,
    signupViewModel: SignupViewModel,
    onLoginStarted: () -> Long = { 0L },
    onAuthFlowEvent: (AuthFlowEvent) -> AuthFlowEventResult = { AuthFlowEventResult.Accepted },
    onAuthFlowCancelled: (Long) -> Unit = {},
): AuthNavigation = remember(
        navigationState,
        loginViewModel,
        signupViewModel,
        onLoginStarted,
        onAuthFlowEvent,
        onAuthFlowCancelled,
    ) {
        AuthNavigation(
            navigationState = navigationState,
            loginViewModel = loginViewModel,
            signupViewModel = signupViewModel,
            onLoginStarted = onLoginStarted,
            onAuthFlowEvent = onAuthFlowEvent,
            onAuthFlowCancelled = onAuthFlowCancelled,
        )
    }

/** 경로 키에 인증 정보를 저장하지 않고 인증 결과를 화면 이동에 연결한다. */
internal class AuthNavigation(
    private val navigationState: AppNavigationState,
    val loginViewModel: LoginViewModel,
    val signupViewModel: SignupViewModel,
    private val onLoginStarted: () -> Long = { 0L },
    private val onAuthFlowEvent: (AuthFlowEvent) -> AuthFlowEventResult = {
        AuthFlowEventResult.Accepted
    },
    private val onAuthFlowCancelled: (Long) -> Unit = {},
) {
    fun isActive(route: AppRoute, displayedRoute: AppRoute): Boolean =
        navigationState.isCurrentRoute(route) && displayedRoute == route

    fun onLoginStarted(): Long = onLoginStarted.invoke()

    fun onLoginAttemptRejected(attemptId: Long) = onAuthFlowCancelled(attemptId)

    @Suppress("UNUSED_PARAMETER")
    fun isBackBlocked(displayedRoute: AppRoute, authSessionState: AuthSessionState): Boolean =
        when (displayedRoute) {
            AppRoute.Login -> true
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
        if (route == AppRoute.TermsAgreement) {
            val attemptId = signupViewModel.uiState.authFlowAttemptId
            signupViewModel.resetSignup()
            attemptId?.let(onAuthFlowCancelled)
        }
        navigationState.popBackStack(route)
    }

    fun onAuthenticated(displayedRoute: AppRoute, attemptId: Long, eventId: Long): Boolean {
        if (!isActive(AppRoute.Login, displayedRoute)) return false
        val result = onAuthFlowEvent(
            AuthFlowEvent(
                attemptId = attemptId,
                eventId = eventId,
                type = AuthFlowEventType.LoginAuthenticated,
            ),
        )
        if (result == AuthFlowEventResult.Rejected) return false
        if (result == AuthFlowEventResult.Accepted) signupViewModel.resetSignup()
        return true
    }

    fun onSignupRequired(
        displayedRoute: AppRoute,
        attemptId: Long,
        eventId: Long,
        signupToken: String,
        provider: AnalyticsAuthProvider,
    ): Boolean {
        if (!isActive(AppRoute.Login, displayedRoute)) return false
        val result = onAuthFlowEvent(
            AuthFlowEvent(
                attemptId = attemptId,
                eventId = eventId,
                type = AuthFlowEventType.SignupRequired,
            ),
        )
        if (result == AuthFlowEventResult.Rejected) return false
        if (result == AuthFlowEventResult.AlreadyHandled) return true
        signupViewModel.startSignup(signupToken, provider, attemptId)
        navigationState.navigate(AppRoute.TermsAgreement)
        return true
    }

    fun onSignupCompleted(
        displayedRoute: AppRoute,
        attemptId: Long,
        eventId: Long,
    ): Boolean {
        if (!isActive(AppRoute.TermsAgreement, displayedRoute)) return false
        if (signupViewModel.uiState.authFlowAttemptId != attemptId) return false
        return onAuthFlowEvent(
            AuthFlowEvent(
                attemptId = attemptId,
                eventId = eventId,
                type = AuthFlowEventType.SignupCompleted,
            ),
        ) != AuthFlowEventResult.Rejected
    }

    fun onMissingSignup(displayedRoute: AppRoute) {
        if (
            isActive(AppRoute.TermsAgreement, displayedRoute) &&
            !signupViewModel.uiState.hasPendingSignup
        ) {
            signupViewModel.uiState.authFlowAttemptId?.let(onAuthFlowCancelled)
            navigationState.navigate(AppRoute.Login)
        }
    }
}
