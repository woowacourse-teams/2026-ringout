package com.joon.ringout.presentation.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.joon.ringout.domain.auth.AuthRepository
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.mypage.MyPageViewModel

@Composable
internal fun AuthSessionCoordinator(
    authRepository: AuthRepository,
    authSessionState: AuthSessionState,
    myPageViewModel: MyPageViewModel?,
    destinationViewModel: DestinationViewModel?,
) {
    AuthSessionStateBinding(
        authSessionState = authSessionState,
        myPageViewModel = myPageViewModel,
        destinationViewModel = destinationViewModel,
    )
    AuthSessionRestoreEffect(authRepository)
}

@Composable
private fun AuthSessionStateBinding(
    authSessionState: AuthSessionState,
    myPageViewModel: MyPageViewModel?,
    destinationViewModel: DestinationViewModel?,
) {
    val sessionStateHandler = remember(myPageViewModel, destinationViewModel) {
        AuthSessionStateHandler(
            onSessionRestoring = { myPageViewModel?.onSessionRestoring() },
            onLoggedOut = { myPageViewModel?.onLoggedOut() },
            onAuthenticated = { myPageViewModel?.onAuthenticated() },
            onDestinationLoggedOut = { destinationViewModel?.onLoggedOut() },
        )
    }
    LaunchedEffect(authSessionState, sessionStateHandler) {
        sessionStateHandler.onSessionStateChanged(authSessionState)
    }
}

@Composable
private fun AuthSessionRestoreEffect(authRepository: AuthRepository) {
    LaunchedEffect(authRepository) {
        authRepository.restoreSession()
    }
}

internal class AuthSessionStateHandler(
    private val onSessionRestoring: () -> Unit,
    private val onLoggedOut: () -> Unit,
    private val onAuthenticated: () -> Unit,
    private val onDestinationLoggedOut: () -> Unit,
) {
    fun onSessionStateChanged(authSessionState: AuthSessionState) {
        when (authSessionState) {
            AuthSessionState.Restoring -> onSessionRestoring()
            AuthSessionState.Unauthenticated,
            AuthSessionState.ReauthenticationRequired,
            -> {
                onLoggedOut()
                onDestinationLoggedOut()
            }

            AuthSessionState.Authenticated -> onAuthenticated()
        }
    }
}
