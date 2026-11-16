package com.joon.ringout.presentation.developertools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.joon.ringout.domain.auth.AuthDiagnostics
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.developertools.component.AuthExperimentSection
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.domain.developertools.AppBuildInfo
import com.joon.ringout.domain.push.PushTokenRegistrationReadiness
import com.joon.ringout.domain.outbox.OutboxReader

@Composable
fun DeveloperToolsRoute(
    buildInfo: AppBuildInfo,
    pushTokenRegistrationReadiness: PushTokenRegistrationReadiness,
    outboxReader: OutboxReader,
    onBack: () -> Unit,
    remoteConfig: RemoteConfigUiState? = null,
    onRefreshRemoteConfig: () -> Unit = {},
    authDiagnostics: AuthDiagnostics? = null,
    authSession: AuthSession? = null,
) {
    val toolsViewModel = viewModel { DeveloperToolsViewModel(buildInfo, outboxReader) }
    LaunchedEffect(toolsViewModel) { toolsViewModel.refreshOutbox() }
    val toolsUiState by toolsViewModel.uiState.collectAsState()
    val registrationStatus by pushTokenRegistrationReadiness.registrationStatus.collectAsState()
    DeveloperToolsScreen(
        uiState = toolsUiState.copy(
            pushTokenRegistrationStatus = registrationStatus,
            remoteConfig = remoteConfig,
        ),
        onBack = onBack,
        onRefreshRemoteConfig = onRefreshRemoteConfig,
        onRefreshOutbox = toolsViewModel::refreshOutbox,
        authExperiments = {
            if (authDiagnostics?.isAvailable == true && authSession != null) {
                val experiments = viewModel { AuthExperimentViewModel(authDiagnostics) }
                val state by experiments.state.collectAsState()
                val session by authSession.state.collectAsState()
                LaunchedEffect(experiments) { experiments.observeTokenStatus() }
                LaunchedEffect(session) {
                    if (session == AuthSessionState.ReauthenticationRequired) onBack()
                }
                AuthExperimentSection(
                    state = state,
                    session = session,
                    onRevoke = experiments::revoke,
                    onRun = experiments::run,
                    onRetry = experiments::retry,
                )
            }
        },
    )
}
